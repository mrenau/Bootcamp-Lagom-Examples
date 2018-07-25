package sample.chirper.friend.impl

import akka.Done
import com.datastax.driver.core.{BoundStatement, PreparedStatement}
import com.lightbend.lagom.scaladsl.persistence.ReadSideProcessor
import com.lightbend.lagom.scaladsl.persistence.cassandra.{CassandraReadSide, CassandraSession}

import scala.concurrent.{ExecutionContext, Future}

class FriendEventProcessor(
                            session: CassandraSession,
                            readSide: CassandraReadSide
                          )(implicit ec: ExecutionContext) extends ReadSideProcessor[FriendEvent] {

  private var writeFollowers: PreparedStatement = _

  override def buildHandler() = readSide.builder[FriendEvent]("friend_offset")
    .setGlobalPrepare(prepareCreateTables)
    .setPrepare(_ => prepareWriteFollowers())
    .setEventHandler[FriendAdded](ese => processFriendChanged(ese.event))
    .build()

  override def aggregateTags = Set(FriendEvent.Tag)

  // Helpers -----------------------------------------------------------------------------------------------------------

  private def prepareCreateTables(): Future[Done] = {
    session.executeCreateTable(
      """CREATE TABLE IF NOT EXISTS follower (
        |userId text,followedBy text,
        |PRIMARY KEY (userId, followedBy)
        |)""".stripMargin)
  }

  private def prepareWriteFollowers(): Future[Done] = {
    session.prepare("INSERT INTO follower (userId, followedBy) VALUES (?, ?)").map { ps =>
      writeFollowers = ps
      Done
    }
  }

  private def processFriendChanged(event: FriendAdded): Future[List[BoundStatement]] = {
    val bindWriteFollowers = writeFollowers.bind
    bindWriteFollowers.setString("userId", event.friendId)
    bindWriteFollowers.setString("followedBy", event.userId)
    Future.successful(List(bindWriteFollowers))
  }

}
