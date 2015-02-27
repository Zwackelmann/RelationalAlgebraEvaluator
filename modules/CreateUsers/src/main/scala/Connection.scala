import java.sql.{Connection => JavaConnection, DriverManager, ResultSet}

object Connection {
  val conn_str = Property("db_url") + "?user=" + Property("db_user") + "&password=" + Property("db_pass")
  classOf[com.mysql.jdbc.Driver]

  def withConnection[T] (fun: JavaConnection => T) = {
    val conn = DriverManager.getConnection(conn_str)
    try {
        fun(conn)
    } finally {
        conn.close()
    }
  }
}