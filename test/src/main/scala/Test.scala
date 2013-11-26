import java.sql.{Connection, DriverManager}
import org.jooq.SQLDialect
import org.jooq.impl.DSL.{using => createJooqContext}

object Test {

  Class.forName("org.h2.Driver")

  def main(args: Array[String]): Unit = {

    var dbaConnection: Connection = null

    try {
      {
        val connection = DriverManager.getConnection("jdbc:h2:mem:docs")
        dbaConnection = connection
        val jooq = createJooqContext(connection, SQLDialect.H2)

        jooq.execute(
          """
            |create role basic;
            |create user chris password 'abc';
            |create user bob password 'abc';
            |grant basic to chris;
            |grant basic to bob;
            |create table doc ( title varchar(12), marking_id int );
            |create view doc_view as select doc.* from doc join mac.marking_see on doc.marking_id = mac.marking_see.marking_id;
            |grant select on doc_view to basic;
            |grant select on mac.marking_see to basic;
            |
            |grant select on mac.missing_credentials to basic;
            |grant select on mac.session_credential to basic;
            |grant select on mac.session_credential_not to basic;
            |
            |alter table doc add foreign key ( marking_id ) references mac.marking ( marking_id );
            |insert into doc ( title, marking_id ) values ( 'puppies.jpg', 1 );
            |insert into doc ( title, marking_id ) values ( 'moonbase.doc', 3 );
          """.stripMargin
        )

        println(jooq.fetch("select * from mac.marking_credential"))
      }
      {

        val connection = DriverManager.getConnection("jdbc:h2:mem:docs", "chris", "abc")
        val jooq = createJooqContext(connection, SQLDialect.H2)

        println("user:")
        println(jooq.fetch("select user() from dual"))

        //println("marking_credential:")
        //println(jooq.fetch("select * from mac.marking_credential"))

        println("Missing credentials:")
        println(jooq.fetch("select * from mac.missing_credentials"))

        println("Session credentials:")
        println(jooq.fetch("select * from mac.session_credential"))

        println("Not credentials:")
        println(jooq.fetch("select * from mac.session_credential_not"))

        println("Markings I can see:")
        println(jooq.fetch("select * from mac.marking_see"))

        println("Docs I can see:")
        println(jooq.fetch("select * from doc_view"))
        //println(jooq.fetch("show tables from mac"))
        //println(jooq.fetch("select MAC(1) a, MAC(2) b from dual"))
      }
    } finally {
      if (dbaConnection != null) dbaConnection.close()
    }
  }
}
