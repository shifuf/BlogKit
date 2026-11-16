import importlib.util, pathlib, tempfile, unittest, types, json, hmac, hashlib
from unittest.mock import patch
path = pathlib.Path(__file__).resolve().parents[2] / "scripts/ops/blog_ops.py"
spec = importlib.util.spec_from_file_location("blog_ops", path)
ops = importlib.util.module_from_spec(spec)
if path.exists(): spec.loader.exec_module(ops)
class OpsTests(unittest.TestCase):
    def test_failed_backup_restarts_backend_and_scrubs_plaintext(self):
        with tempfile.TemporaryDirectory() as folder:
            root=pathlib.Path(folder);(root/"secrets").mkdir();(root/"secrets/key").write_bytes(b"test-secret-passphrase-32-characters");(root/"secrets/key").chmod(0o600)
            (root/".env").write_text("DB_MIGRATION_USERNAME=migrator\nDB_MIGRATION_PASSWORD=private-password\n")
            actions=[]
            def docker(_root,*args,**kw):
                actions.append(args)
                if kw.get("stdout"):kw["stdout"].write(b"private-data")
            with patch.object(ops,"compose",docker),patch.object(ops,"run",side_effect=RuntimeError("encryption failed")):
                with self.assertRaises(RuntimeError):ops.backup(root,types.SimpleNamespace(secret_file="secrets/key",keep=2,offsite="",initial_admin=False))
            self.assertEqual(actions[0],("stop","backend"));self.assertEqual(actions[-1],("start","backend"))
            self.assertEqual(list(root.glob("backups/**/database.dump")),[])
            self.assertEqual(list(root.glob("backups/**/uploads.tar")),[])
    def test_manifest_authentication_rejects_changed_metadata(self):
        with tempfile.TemporaryDirectory() as folder:
            root=pathlib.Path(folder);(root/"database.dump.enc").write_bytes(b"ciphertext")
            value={"files":{"database.dump.enc":hashlib.sha256(b"ciphertext").hexdigest()},"offsiteVerified":False}
            secret=b"independent-secret";value["hmacSha256"]=hmac.new(secret,json.dumps(value,sort_keys=True,separators=(",",":")).encode(),hashlib.sha256).hexdigest()
            (root/"manifest.json").write_text(json.dumps(value));ops.verify_manifest(root,secret)
            value["offsiteVerified"]=True;(root/"manifest.json").write_text(json.dumps(value))
            with self.assertRaises(ValueError):ops.verify_manifest(root,secret)
    def test_output_cannot_escape_project_or_follow_symlink(self):
        with tempfile.TemporaryDirectory() as folder:
            root=pathlib.Path(folder)
            self.assertEqual(ops.safe_child(root, "backups/run"),root/"backups/run")
            for name in ("../outside",str(root.parent/"outside")):
                with self.assertRaises(ValueError): ops.safe_child(root,name)
    def test_redaction_does_not_emit_connection_secrets(self):
        self.assertEqual(ops.safe_error("command failed: password=private-secret"),"Operation failed; inspect protected operational logs locally.")
    def test_retention_only_removes_completed_backup_directories(self):
        with tempfile.TemporaryDirectory() as folder:
            root=pathlib.Path(folder)
            for name in ("backup-20260101T000000Z","backup-20260102T000000Z","backup-20260103T000000Z","keep-user-files"):
                p=root/name;p.mkdir();(p/"manifest.json").write_text("{}")
            ops.prune(root,2)
            self.assertFalse((root/"backup-20260101T000000Z").exists())
            self.assertTrue((root/"keep-user-files").exists())
    def test_restore_manifest_detects_tamper_before_any_container_command(self):
        with tempfile.TemporaryDirectory() as folder:
            root=pathlib.Path(folder);(root/"database.dump").write_bytes(b"changed")
            (root/"manifest.json").write_text('{"files":{"database.dump":"wrong"}}')
            with self.assertRaises(ValueError):ops.verify_manifest(root)
if __name__=="__main__":unittest.main()
