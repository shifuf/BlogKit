#!/usr/bin/env python3
"""Project-scoped backup/deployment preflight. No remote SSH; secrets never in argv/logs."""
import argparse, datetime, hashlib, hmac, json, os, pathlib, re, secrets, shutil, subprocess, sys, tarfile
ALLOWED = {"/www/wwwroot/blog", "/www/wwwroot/unified-file-manager"}
BACKUP_NAME = re.compile(r"backup-\d{8}T\d{6}Z")
def safe_child(root, name):
    root=pathlib.Path(root).resolve(); candidate=(root/name).resolve()
    if candidate == root or root not in candidate.parents: raise ValueError("Path outside project")
    return candidate

def safe_error(_error): return "Operation failed; inspect protected operational logs locally."
def run(args, *, env=None, stdin=None, stdout=None):
    result=subprocess.run(args,input=stdin,stdout=stdout if stdout is not None else subprocess.PIPE,stderr=subprocess.PIPE,env=env,check=False)
    if result.returncode: raise RuntimeError("Operational command failed")
    return result.stdout

def compose(root, *args, **kw): return run(["docker","compose","--project-directory",str(root),*args],**kw)
def envfile(root):
    # .env values are not shell-evaluated. Deployment secrets should use plain values.
    values={}
    for line in (root/".env").read_text(encoding="utf-8-sig").splitlines():
        if line.strip() and not line.lstrip().startswith("#") and "=" in line:
            k,v=line.split("=",1);values[k.strip()]=v.strip().strip('"').strip("'")
    return values

def verify_manifest(directory, secret=None):
    data=json.loads((directory/"manifest.json").read_text())
    if secret is not None:
        expected=data.pop("hmacSha256",None)
        actual=hmac.new(secret,json.dumps(data,sort_keys=True,separators=(",",":")).encode(),hashlib.sha256).hexdigest()
        if not expected or not hmac.compare_digest(expected,actual):raise ValueError("Backup authentication failed")
    if not isinstance(data.get("files"),dict) or not data["files"]: raise ValueError("Missing file inventory")
    for name,digest in data["files"].items():
        path=safe_child(directory,name)
        if not path.is_file() or sha(path)!=digest:raise ValueError("Backup checksum mismatch")
    return data

def sha(path):
    digest=hashlib.sha256()
    with path.open("rb") as stream:
        while chunk:=stream.read(1024*1024):digest.update(chunk)
    return digest.hexdigest()

def prune(root, keep):
    if keep<2:raise ValueError("At least two backups required")
    entries=sorted([p for p in root.iterdir() if BACKUP_NAME.fullmatch(p.name) and p.is_dir() and not p.is_symlink() and (p/"manifest.json").is_file()],reverse=True)
    for entry in entries[keep:]:
        checked=safe_child(root,entry.name)
        if checked!=entry:raise ValueError("Backup path changed")
        shutil.rmtree(checked)

def backup(root,args):
    config=envfile(root); backups=safe_child(root,"backups/physical");backups.mkdir(parents=True,exist_ok=True)
    stamp=datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    stage=safe_child(backups,".partial-"+stamp);stage.mkdir(mode=0o700)
    user=config.get("DB_ADMIN_USERNAME" if args.initial_admin else "DB_MIGRATION_USERNAME"); password=config.get("DB_ADMIN_PASSWORD" if args.initial_admin else "DB_MIGRATION_PASSWORD"); database=config.get("POSTGRES_DB","blog_system")
    if not user or not password:raise ValueError("Migration dump credentials required")
    secret=safe_child(root,args.secret_file)
    if not secret.is_file() or secret.stat().st_size<32:raise ValueError("Independent backup encryption passphrase file required")
    if os.name!="nt" and secret.stat().st_mode & 0o077:raise ValueError("Passphrase file must be mode0600")
    # pg_dump -Fc uses one MVCC snapshot. No application writes while uploads/config captured.
    compose(root,"stop","backend")
    try:
        with (stage/"database.dump").open("wb") as output:
            compose(root,"exec","-T","-e","PGPASSWORD","postgres","pg_dump","-U",user,"-d",database,"--format=custom","--no-owner","--no-privileges",env={**os.environ,"PGPASSWORD":password},stdout=output)
        with (stage/"uploads.tar").open("wb") as output:
            compose(root,"run","--rm","--no-deps","--entrypoint","tar","backend","-C","/app/uploads","-cf","-",".",stdout=output)
        # Keep keys separate from data archive; encrypted with independently held passphrase.
        secure=stage/"configuration.tar.enc"
        archive=tarfile.TarInfo(".env");payload=(root/".env").read_bytes();archive.size=len(payload);archive.mode=0o600
        import io
        memory=io.BytesIO()
        with tarfile.open(fileobj=memory,mode="w") as target:target.addfile(archive,io.BytesIO(payload))
        run(["openssl","enc","-aes-256-cbc","-salt","-pbkdf2","-iter","200000","-pass","file:"+str(secret),"-out",str(secure)],stdin=memory.getvalue())
        # Encrypt data as well; leave no plaintext pg dump/uploads at rest after success.
        for filename in ("database.dump","uploads.tar"):
            source=stage/filename
            run(["openssl","enc","-aes-256-cbc","-salt","-pbkdf2","-iter","200000","-pass","file:"+str(secret),"-in",str(source),"-out",str(source)+".enc"]);source.unlink()
        files={p.name:sha(p) for p in stage.iterdir() if p.is_file()}
        manifest={"format":1,"createdAt":stamp,"consistency":"backend-stopped; pg_dump MVCC snapshot","offsiteConfigured":bool(args.offsite),"offsiteVerified":False,"files":files}
        manifest["hmacSha256"]=hmac.new(secret.read_bytes(),json.dumps(manifest,sort_keys=True,separators=(",",":")).encode(),hashlib.sha256).hexdigest()
        (stage/"manifest.json").write_text(json.dumps(manifest,indent=2))
        final=safe_child(backups,"backup-"+stamp);stage.rename(final)
        if args.offsite:
            if not re.fullmatch(r"[A-Za-z0-9_-]+:[A-Za-z0-9_./-]+",args.offsite):raise ValueError("Invalid rclone destination")
            destination=args.offsite.rstrip("/")+"/"+final.name
            run(["rclone","copy",str(final),destination]);run(["rclone","check",str(final),destination,"--download"])
            (final/"offsite-verified.json").write_text(json.dumps({"verifiedAt":datetime.datetime.now(datetime.timezone.utc).isoformat()}))
        prune(backups,args.keep)
        print(json.dumps({"backup":str(final),"offsiteVerified":bool(args.offsite)}))
    finally:
        for name in ("database.dump","uploads.tar"):
            if stage.exists(): (stage/name).unlink(missing_ok=True)
        # Start the existing container without applying new dependency health rules
        # during a pre-upgrade backup. Compose start may otherwise leave it stopped.
        run(["docker","start",config.get("BACKEND_CONTAINER_NAME","blog-backend")])

def drill(root,args):
    directory=safe_child(root,args.backup)
    secret=safe_child(root,args.secret_file)
    verify_manifest(directory,secret.read_bytes())
    working=safe_child(root,"backups/drill-"+secrets.token_hex(6));working.mkdir(parents=True,mode=0o700)
    data=working/"postgres";data.mkdir();dump=working/"database.dump"
    name="blog-restore-drill-"+secrets.token_hex(6);password=secrets.token_urlsafe(32)
    started=False
    try:
        run(["openssl","enc","-d","-aes-256-cbc","-pbkdf2","-iter","200000","-pass","file:"+str(secret),"-in",str(directory/"database.dump.enc"),"-out",str(dump)])
        run(["docker","run","--detach","--name",name,"--network","none","--memory","256m","--cpus","0.5","--label","blog.restore-drill=true","--mount",f"type=bind,src={data},dst=/var/lib/postgresql/data","-e","POSTGRES_PASSWORD",args.image],env={**os.environ,"POSTGRES_PASSWORD":password})
        started=True
        import time
        for attempt in range(60):
            try:run(["docker","exec",name,"pg_isready","-U","postgres"]);break
            except RuntimeError:
                if attempt==59:raise
                time.sleep(1)
        with dump.open("rb") as source:
            result=subprocess.run(["docker","exec","-i",name,"pg_restore","-U","postgres","-d","postgres","--exit-on-error","--no-owner","--no-privileges"],stdin=source,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
            if result.returncode:raise RuntimeError("Restore failed")
        counts=run(["docker","exec",name,"psql","-U","postgres","-d","postgres","-Atc","SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE'"])
        if int(counts.strip())<1:raise ValueError("Restore produced no tables")
        (working/"result.json").write_text(json.dumps({"restoredTables":int(counts.strip()),"isolatedNetwork":True,"primaryDatabaseTouched":False}))
        print("Restore drill succeeded; isolated database retained under project backups for inspection.")
    finally:
        if started:run(["docker","rm","--force",name])
        dump.unlink(missing_ok=True)

def preflight(root,args):
    config=envfile(root)
    for key in ('POSTGRES_VOLUME_NAME','REDIS_VOLUME_NAME','UPLOADS_VOLUME_NAME'):
        name=config.get(key)
        if not name or not re.fullmatch(r'[A-Za-z0-9_.-]+',name):raise ValueError('Explicit volume names required')
        run(['docker','volume','inspect',name])
    for key in ('DB_ADMIN_USERNAME','DB_USERNAME','DB_MIGRATION_USERNAME','BLOG_BACKUP_DB_USERNAME','DB_ADMIN_PASSWORD','DB_PASSWORD','DB_MIGRATION_PASSWORD','BLOG_BACKUP_DB_PASSWORD','REDIS_PASSWORD'):
        if not config.get(key):raise ValueError('Role configuration incomplete')
    if len({config[key] for key in ('DB_ADMIN_USERNAME','DB_USERNAME','DB_MIGRATION_USERNAME','BLOG_BACKUP_DB_USERNAME')})!=4:raise ValueError('Database roles must be distinct')
    compose(root,'config','--quiet')
    stat=os.statvfs(root) if hasattr(os,'statvfs') else None
    if stat and stat.f_bavail*stat.f_frsize<2*1024**3:raise ValueError('At least2GiB free required before backup/deploy')
    print('Preflight passed: configured existing volumes and role separation verified; inspect capacity and mounts before deployment.')

def configure_roles(root,args):
    config=envfile(root)
    if not args.confirm_owner_transfer:raise ValueError('Explicit schema ownership transfer acknowledgment required')
    keys=('DB_USERNAME','DB_PASSWORD','DB_MIGRATION_USERNAME','DB_MIGRATION_PASSWORD','BLOG_BACKUP_DB_USERNAME','BLOG_BACKUP_DB_PASSWORD')
    if any(not config.get(k) for k in keys):raise ValueError('Missing roles')
    values={**os.environ,**{k:config[k] for k in keys}}
    flags=[]
    for key in keys:flags.extend(['-e',key])
    compose(root,'exec','-T',*flags,'postgres','psql','--no-psqlrc','-U',config['DB_ADMIN_USERNAME'],'-d',config.get('POSTGRES_DB','blog_system'),stdin=(root/'scripts/ops/configure-db-roles.sql').read_bytes(),env=values)
    print('Database roles configured; original administrator preserved and Flyway history not rewritten.')

def main():
    parser=argparse.ArgumentParser();parser.add_argument("--root",required=True)
    subs=parser.add_subparsers(dest="action",required=True)
    p=subs.add_parser("backup");p.add_argument("--secret-file",required=True);p.add_argument("--keep",type=int,default=7);p.add_argument("--offsite",default="");p.add_argument('--initial-admin',action='store_true')
    p=subs.add_parser("restore-drill");p.add_argument("--backup",required=True);p.add_argument("--secret-file",required=True);p.add_argument("--image",default="postgres:17-alpine")
    subs.add_parser('preflight')
    p=subs.add_parser('configure-roles');p.add_argument('--confirm-owner-transfer',action='store_true')
    args=parser.parse_args();root=pathlib.Path(args.root).resolve()
    if str(root) not in ALLOWED or str(root)!="/www/wwwroot/blog":raise ValueError("Unsupported project path")
    os.umask(0o077)
    {"backup":backup,"restore-drill":drill,'preflight':preflight,'configure-roles':configure_roles}[args.action](root,args)
if __name__=="__main__":
    try:main()
    except Exception as error:print(safe_error(error),file=sys.stderr);sys.exit(1)
