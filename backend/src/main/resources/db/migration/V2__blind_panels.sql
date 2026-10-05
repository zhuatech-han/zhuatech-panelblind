-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
create table panel_session (
 id bigint auto_increment primary key,
 reference varchar(60) not null,
 name varchar(160) not null,
 department_id bigint not null,
 category varchar(60) not null,
 instructions varchar(2000) not null,
 reviewer_id bigint not null,
 custodian_id bigint not null,
 created_by bigint not null,
 status varchar(30) not null,
 version bigint not null,
 outcome varchar(20),
 layout_hash varchar(64),
 sealed_hash varchar(64),
 result_hash varchar(64),
 approved_by bigint,
 approved_at timestamp(6),
 sealed_by bigint,
 sealed_at timestamp(6),
 release_requested_by bigint,
 released_by bigint,
 released_at timestamp(6),
 created_at timestamp(6) not null
);
create table session_editor (
 id bigint auto_increment primary key,
 session_id bigint not null,
 actor_id bigint not null
);
create table blind_sample (
 id bigint auto_increment primary key,
 session_id bigint not null,
 code varchar(60) not null,
 name varchar(160) not null,
 description varchar(1000) not null
);
create table rating_scale (
 id bigint auto_increment primary key,
 session_id bigint not null,
 code varchar(60) not null,
 name varchar(120) not null,
 minimum int not null,
 maximum int not null,
 low_anchor varchar(120) not null,
 high_anchor varchar(120) not null,
 required boolean not null
);
create table panel_sheet (
 id bigint auto_increment primary key,
 session_id bigint not null,
 rater_id bigint not null,
 status varchar(30) not null,
 version bigint not null,
 acknowledged_at timestamp(6),
 submitted_at timestamp(6),
 accepted_by bigint,
 accepted_at timestamp(6),
 withdrawal_reason varchar(1000),
 response_hash varchar(64)
);
create table presentation (
 id bigint auto_increment primary key,
 sheet_id bigint not null,
 sample_id bigint not null,
 blind_code varchar(3) not null,
 position int not null
);
create table rating_value (
 id bigint auto_increment primary key,
 presentation_id bigint not null,
 scale_id bigint not null,
 score_value int,
 missing_reason varchar(500) not null,
 note varchar(500) not null,
 updated_at timestamp(6) not null
);
create table command_record (id bigint auto_increment primary key,request_key varchar(36) not null unique,fingerprint varchar(64) not null,response_json longtext not null);
create table business_event (id bigint auto_increment primary key,object_type varchar(30) not null,object_id bigint not null,actor_id bigint not null,action varchar(60) not null,note varchar(1000) not null,snapshot longtext not null,created_at timestamp(6) not null);
alter table panel_session add constraint fk_panel_session_department_id foreign key (department_id) references department(id);
alter table panel_session add constraint fk_panel_session_reviewer_id foreign key (reviewer_id) references account(id);
alter table panel_session add constraint fk_panel_session_custodian_id foreign key (custodian_id) references account(id);
alter table panel_session add constraint fk_panel_session_created_by foreign key (created_by) references account(id);
alter table panel_session add constraint fk_panel_session_approved_by foreign key (approved_by) references account(id);
alter table panel_session add constraint fk_panel_session_sealed_by foreign key (sealed_by) references account(id);
alter table panel_session add constraint fk_panel_session_release_requested_by foreign key (release_requested_by) references account(id);
alter table panel_session add constraint fk_panel_session_released_by foreign key (released_by) references account(id);
alter table session_editor add constraint fk_session_editor_session_id foreign key (session_id) references panel_session(id);
alter table session_editor add constraint fk_session_editor_actor_id foreign key (actor_id) references account(id);
alter table blind_sample add constraint fk_blind_sample_session_id foreign key (session_id) references panel_session(id);
alter table rating_scale add constraint fk_rating_scale_session_id foreign key (session_id) references panel_session(id);
alter table panel_sheet add constraint fk_panel_sheet_session_id foreign key (session_id) references panel_session(id);
alter table panel_sheet add constraint fk_panel_sheet_rater_id foreign key (rater_id) references account(id);
alter table panel_sheet add constraint fk_panel_sheet_accepted_by foreign key (accepted_by) references account(id);
alter table presentation add constraint fk_presentation_sheet_id foreign key (sheet_id) references panel_sheet(id);
alter table presentation add constraint fk_presentation_sample_id foreign key (sample_id) references blind_sample(id);
alter table rating_value add constraint fk_rating_value_presentation_id foreign key (presentation_id) references presentation(id);
alter table rating_value add constraint fk_rating_value_scale_id foreign key (scale_id) references rating_scale(id);
alter table business_event add constraint fk_business_event_actor_id foreign key (actor_id) references account(id);
alter table panel_session add constraint uq_panel_session unique(reference);
alter table session_editor add constraint uq_session_editor unique(session_id,actor_id);
alter table blind_sample add constraint uq_blind_sample unique(session_id,code);
alter table rating_scale add constraint uq_rating_scale unique(session_id,code);
alter table panel_sheet add constraint uq_panel_sheet unique(session_id,rater_id);
alter table presentation add constraint uq_presentation unique(sheet_id,position);
alter table rating_value add constraint uq_rating_value unique(presentation_id,scale_id);
create index idx_event_obj on business_event(object_type,object_id);
