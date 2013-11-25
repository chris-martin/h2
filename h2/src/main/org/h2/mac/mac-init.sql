-- tables

create table mac.marking
  (
    marking_id int not null,
    sensitivity_id int not null
  )
  ;
create table mac.sensitivity
  (
    sensitivity_id int not null,
    name varchar(32) not null
  )
  ;
create table mac.compartment
  (
    compartment_id int not null,
    name varchar(32) not null
  )
  ;
create table mac.marking_compartment
  (
    marking_id int not null,
    compartment_id int not null
  )
  ;
create table mac.credential
  (
    credential_id int not null,
    sensitivity_id int not null,
    compartment_id int not null
  )
  ;
create table mac.session_credential
  (
    credential_id int not null
  )
  ;

-- primary keys

alter table mac.sensitivity
  add primary key ( sensitivity_id )
  ;
alter table mac.marking
  add primary key ( marking_id )
  ;
alter table mac.marking_compartment
  add primary key ( marking_id, compartment_id )
  ;
alter table mac.credential
  add primary key ( credential_id )
  ;

alter table mac.session_credential
  add primary key ( credential_id )
  ;

-- foreign keys

alter table mac.marking
  add foreign key ( sensitivity_id )
  references mac.sensitivity ( sensitivity_id )
  ;
alter table mac.marking_compartment
  add foreign key ( marking_id )
  references mac.marking ( marking_id )
  ;
alter table mac.marking_compartment
  add foreign key ( compartment_id )
  references mac.compartment ( compartment_id )
  ;
alter table mac.session_credential
  add foreign key ( credential_id )
  references mac.credential ( credential_id )
  ;

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
  and mac.marking_compartment.compartment_id = mac.credential.compartment_id
  ;

create view mac.session_credential_not as
  (
    select mac.credential.credential_id credential_id
    from mac.credential
  )
  minus
  (
    select mac.session_credential.credential_id credential_id
    from mac.session_credential
  )
  ;

create view mac.marking_see as
  select mac.marking.marking_id marking_id
  from mac.marking
  where not exists (
    (
      select mac.marking_credential.credential_id credential_id
      from mac.marking_credential
      where mac.marking_credential.marking_id = mac.marking.marking_id
    )
    intersect
    (
      select mac.session_credential_not.credential_id credential_id
      from mac.session_credential_not
    )
  )
  ;

-- data

insert into mac.sensitivity ( sensitivity_id, name ) values ( 0, 'PUBLIC' );
insert into mac.sensitivity ( sensitivity_id, name ) values ( 1, 'WORKER' );
insert into mac.sensitivity ( sensitivity_id, name ) values ( 2, 'MANAGER' );
insert into mac.sensitivity ( sensitivity_id, name ) values ( 3, 'EXECUTIVE' );

insert into mac.compartment ( compartment_id, name ) values ( 0, 'GENERAL' );
insert into mac.compartment ( compartment_id, name ) values ( 1, 'APPLES' );
insert into mac.compartment ( compartment_id, name ) values ( 2, 'BANANAS' );

insert into mac.credential ( credential_id, sensitivity_id, compartment_id ) values ( 1, 1, 0 );
insert into mac.credential ( credential_id, sensitivity_id, compartment_id ) values ( 2, 1, 1 );
insert into mac.credential ( credential_id, sensitivity_id, compartment_id ) values ( 3, 1, 2 );
insert into mac.credential ( credential_id, sensitivity_id, compartment_id ) values ( 4, 2, 0 );
insert into mac.credential ( credential_id, sensitivity_id, compartment_id ) values ( 5, 2, 1 );
insert into mac.credential ( credential_id, sensitivity_id, compartment_id ) values ( 6, 2, 2 );
insert into mac.credential ( credential_id, sensitivity_id, compartment_id ) values ( 7, 3, 0 );
insert into mac.credential ( credential_id, sensitivity_id, compartment_id ) values ( 8, 3, 1 );
insert into mac.credential ( credential_id, sensitivity_id, compartment_id ) values ( 9, 3, 2 );

-- 1 - public
insert into mac.marking ( marking_id, sensitivity_id ) values ( 1, 0 );

-- 2 - worker
insert into mac.marking ( marking_id, sensitivity_id ) values ( 2, 1 );
insert into mac.marking_compartment ( marking_id, compartment_id ) values ( 2, 0 );

-- 3 - manager/apples
insert into mac.marking ( marking_id, sensitivity_id ) values ( 3, 2 );
insert into mac.marking_compartment ( marking_id, compartment_id ) values ( 3, 1 );

-- session credentials
insert into mac.session_credential ( credential_id ) values ( 1 );
insert into mac.session_credential ( credential_id ) values ( 2 );
insert into mac.session_credential ( credential_id ) values ( 3 );
insert into mac.session_credential ( credential_id ) values ( 4 );
insert into mac.session_credential ( credential_id ) values ( 7 );
