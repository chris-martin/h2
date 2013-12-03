create schema mac;

-- tables

create table mac.marking (
  marking_id identity,
  sensitivity_id bigint not null,
  compartment_id_list clob not null );

create table mac.sensitivity (
  sensitivity_id identity,
  name varchar(255) not null );

create table mac.compartment (
  compartment_id identity,
  name varchar(255) not null );

create table mac.marking_compartment (
  marking_id bigint not null,
  compartment_id bigint not null );

create table mac.credential (
  credential_id identity,
  sensitivity_id bigint not null,
  compartment_id bigint not null );

create table mac.user_credential (
  user_name varchar(255) not null,
  credential_id bigint not null );

-- primary keys

alter table mac.marking_compartment
  add primary key ( marking_id, compartment_id );

alter table mac.user_credential
  add primary key ( user_name, credential_id );

-- indexes

create index mac.index_marking_compartment_id_list
on mac.marking ( compartment_id_list );

create index mac.index_marking_sensitivity_id
on mac.marking ( sensitivity_id );

create index mac.index_marking_sensitivity
  on mac.marking ( sensitivity_id );

create index mac.index_sensitivity_name
  on mac.sensitivity ( name );

create index mac.index_compartment_name
  on mac.compartment ( name );

create index mac.index_credential_sensitivity_id
  on mac.credential ( sensitivity_id );

create index mac.index_credential_compartment_id
  on mac.credential ( compartment_id );

create index mac.index_user_credential_user_name
on mac.user_credential ( user_name );

create index mac.index_user_credential_credential_id
on mac.user_credential ( credential_id );

-- foreign keys

alter table mac.marking
  add foreign key ( sensitivity_id )
  references mac.sensitivity ( sensitivity_id );

alter table mac.marking_compartment
  add foreign key ( marking_id )
  references mac.marking ( marking_id );

alter table mac.marking_compartment
  add foreign key ( compartment_id )
  references mac.compartment ( compartment_id );

alter table mac.user_credential
  add foreign key ( credential_id )
  references mac.credential ( credential_id );

-- views

create view mac.marking_credential as
  select
    mac.marking.marking_id marking_id,
    mac.credential.credential_id credential_id
  from mac.marking
  left join mac.marking_compartment
  on mac.marking.marking_id = mac.marking_compartment.marking_id
  join mac.credential
  on mac.marking.sensitivity_id = mac.credential.sensitivity_id
  and mac.marking_compartment.compartment_id = mac.credential.compartment_id;

create view mac.session_credential as
  select mac.user_credential.credential_id credential_id
  from mac.user_credential
  where upper(mac.user_credential.user_name) = upper(user());

create view mac.session_credential_not as
  ( select mac.credential.credential_id credential_id
    from mac.credential )
  minus
  ( select mac.session_credential.credential_id credential_id
    from mac.session_credential );

create view mac.missing_credentials as
  select
    mac.marking_credential.marking_id marking_id,
    mac.marking_credential.credential_id credential_id
  from mac.marking_credential
  join mac.session_credential_not
  on mac.marking_credential.credential_id =
     mac.session_credential_not.credential_id;

create view mac.session_marking as
  select mac.marking.marking_id marking_id
  from mac.marking
  left join mac.missing_credentials
  on mac.marking.marking_id = mac.missing_credentials.marking_id
  where mac.missing_credentials.credential_id is null;

-- data

insert into mac.sensitivity ( sensitivity_id, name )
  values ( 0, '' );

insert into mac.marking ( marking_id, sensitivity_id, compartment_id_list )
  values ( 0, 0, '' );
