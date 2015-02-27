import Connection._
import java.io.File
import java.io.BufferedWriter
import java.io.FileWriter

object Main {
  val validPasswordChars = List(
    'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
    'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0'
  ).toIndexedSeq

  def randomStream(maxInt: Int): Stream[Int] = (math.random*maxInt).toInt #:: randomStream(maxInt)
  def randomPassword(length: Int, chars: Seq[Char]) = new String(randomStream(chars.size).take(length).map(i => chars(i)).toArray)

  def main(args: Array[String]) {
    registerUsers(
      createRdbGroupRange = (1 to 500),
      createSqlGroupRange = (1 to 500),
      overrideExistingUsers = false, 
      passwordLength = 10
    )
    
    exportUserList(
      rdbGroupRange = (1 to 500),
      sqlGroupRange = (1 to 500),
      filename = "/home/simon/rdb_sql_users"
    )
  }

  def existingUsers = withConnection ( c => {
    val rs = c.createStatement().executeQuery(
        """
        SELECT name, password
        FROM users
        """
    )

    Iterator.continually(
      if (rs.next()) Some(Pair(
        rs.getString(1),
        rs.getString(2)
      )) else None
    ).takeWhile(_.isDefined).toList.flatten
  }).toList

  def exportUserList(rdbGroupRange: Range, sqlGroupRange: Range, filename: String) {
    val _existingUsers = existingUsers
    val _allUserNames = allUserNames(rdbGroupRange, sqlGroupRange)

    val exportUsers = _existingUsers.filter(user => _allUserNames.contains(user._1)).toList.sortBy(_._1)

    val writer = new BufferedWriter(new FileWriter(new File(filename)))
    writer.write(exportUsers.map(u => u._1 + " " + u._2).mkString("\n"))
    writer.close
  }

  def intToSqlName(i: Int) = "sql_group_%03d".format(i)
  def intToRdbName(i: Int) = "rdb_group_%03d".format(i)

  def allUserNames(createRdbGroupRange: Range, createSqlGroupRange: Range) = {
    val allSqlUserNames = (createSqlGroupRange map (nr => intToSqlName(nr))).toList
    val allRdbUserNames = (createRdbGroupRange map (nr => intToRdbName(nr))).toList

    allSqlUserNames ++ allRdbUserNames
  }

  def registerUsers(createRdbGroupRange: Range, createSqlGroupRange: Range, overrideExistingUsers: Boolean, passwordLength: Int) {
    val (existingUsernames, nonExistingUserNames) = {
      val existingUserNames = existingUsers.map(_._1)
      val _allUserNames = allUserNames(createRdbGroupRange, createSqlGroupRange)

      (_allUserNames intersect existingUserNames, _allUserNames diff existingUserNames)
    }

    var updateCount = 0
    var skipCount = 0
    var insertCount = 0

    withConnection ( c => {
      for(username <- existingUsernames) {
        if(overrideExistingUsers) {
          c.createStatement().execute("UPDATE users SET password = '" + randomPassword(passwordLength, validPasswordChars) + "' WHERE name='" + username + "'")
          println("updated user " + username)
          updateCount += 1
        } else {
          println("skipped user " + username)
          skipCount += 1
        }
      }

      for(username <- nonExistingUserNames) {
        c.createStatement().execute("INSERT INTO users (name, password) VALUES ('" + username + "', '" + randomPassword(passwordLength, validPasswordChars) + "')")
        println("inserted user " + username)
        insertCount += 1
      }
    })

    println()
    println(updateCount + " users updated")
    println(skipCount + " users skipped")
    println(insertCount + " users inserted")
  }
}
