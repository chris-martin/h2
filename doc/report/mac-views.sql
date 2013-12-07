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
