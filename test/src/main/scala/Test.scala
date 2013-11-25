import java.sql.DriverManager
import org.jooq.SQLDialect
import org.jooq.impl.DSL.{using => createJooqContext}

object Test {
  def main(args: Array[String]): Unit = {
    Class.forName("org.h2.Driver")
    val connection = DriverManager.getConnection("jdbc:h2:mem:")
    val jooq = createJooqContext(connection, SQLDialect.H2)
    jooq.execute("create table doc ( title varchar(12), marking_id int )")
    jooq.execute("alter table doc add foreign key ( marking_id ) references mac.marking ( marking_id )")
    jooq.execute("insert into doc ( title, marking_id ) values ( 'puppies.jpg', 1 )")
    jooq.execute("insert into doc ( title, marking_id ) values ( 'moonbase.doc', 3 )")

    println("marking_credential:")
    println(jooq.fetch("select * from mac.marking_credential"))

    println("Markings I can see:")
    println(jooq.fetch("select * from mac.marking_see"))

    jooq.execute("create view doc_view as select doc.* from doc join mac.marking_see on doc.marking_id = mac.marking_see.marking_id")
    println("Docs I can see:")
    println(jooq.fetch("select * from doc_view"))
    //println(jooq.fetch("show tables from mac"))
    //println(jooq.fetch("select MAC(1) a, MAC(2) b from dual"))
    connection.close()
  }
}
