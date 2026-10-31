import org.bson.Document
import java.util.Date
import scala.jdk.CollectionConverters._
import scala.util.Try

case class Grade(date: Option[Date], grade: String, score: Option[Int])

case class Restaurant(
    restaurantId: String,
    name: String,
    cuisine: String,
    borough: String,
    zipcode: String,
    building: String,
    street: String,
    grades: List[Grade] = Nil
) {

  def averageScore: Option[Double] = {
    val scores = grades.flatMap(_.score)
    if (scores.isEmpty) None else Some(scores.sum.toDouble / scores.size)
  }

  def summary: String = {
    val avg = averageScore match {
      case Some(a) => f"$a%.1f"
      case None    => "no scores"
    }
    s"[$restaurantId] $name | $cuisine | $borough | $zipcode | avg score: $avg"
  }

  def toDocument: Document = {
    val address = new Document("building", building)
      .append("street", street)
      .append("zipcode", zipcode)
    val gradeDocs = grades.map { g =>
      new Document("date", g.date.orNull)
        .append("grade", g.grade)
        .append("score", g.score.map(Int.box).orNull)
    }
    new Document("restaurant_id", restaurantId)
      .append("name", name)
      .append("cuisine", cuisine)
      .append("borough", borough)
      .append("address", address)
      .append("grades", gradeDocs.asJava)
  }
}

object Restaurant {

  private def str(doc: Document, key: String): String =
    Option(doc.get(key)).map(_.toString).getOrElse("")

  private def toGrade(d: Document): Grade =
    Grade(
      date = Try(d.getDate("date")).toOption.flatMap(Option(_)),
      grade = str(d, "grade"),
      score = Option(d.get("score")).collect { case n: Number => n.intValue }
    )

  def fromDocument(doc: Document): Option[Restaurant] =
    Try {
      val address = Option(doc.get("address", classOf[Document])).getOrElse(new Document())
      val gradeDocs = Option(doc.getList("grades", classOf[Document]))
        .map(_.asScala.toList)
        .getOrElse(Nil)
      Restaurant(
        restaurantId = str(doc, "restaurant_id"),
        name = str(doc, "name"),
        cuisine = str(doc, "cuisine"),
        borough = str(doc, "borough"),
        zipcode = str(address, "zipcode"),
        building = str(address, "building"),
        street = str(address, "street"),
        grades = gradeDocs.map(toGrade)
      )
    }.toOption
}
