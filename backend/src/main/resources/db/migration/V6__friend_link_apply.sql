-- 友链申请审核字段
alter table if exists friend_link add column if not exists status varchar(16) default 'APPROVED' not null;
alter table if exists friend_link add column if not exists applicant_email varchar(128);
update friend_link set status = 'APPROVED' where status is null or status = '';
