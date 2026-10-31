import com.mongodb.client.{MongoClient, MongoCollection}
import com.mongodb.client.model.{Accumulators, Aggregates, Filters, Sorts}
import org.bson.Document
import org.bson.conversions.Bson
import scala.jdk.CollectionConverters._
import scala.util.Try

case class TopRated(name: String, cuisine: String, borough: String, avgScore: Double, inspections: Int)

class Analytics(client: MongoClient) {

  private val collection: MongoCollection[Document] =
    client.getDatabase("sample_restaurants").getCollection("restaurants")

  private def run(stages: List[Bson]): Either[String, List[Document]] =
    Try(collection.aggregate(stages.asJava).asScala.toList).toEither.left
      .map(e => s"Aggregation error: ${e.getMessage}")

  private def label(d: Document): String =
    Option(d.get("_id")).map(_.toString).getOrElse("(missing)")

  private def counts(docs: List[Document]): List[(String, Int)] =
    docs.map(d => (label(d), d.getInteger("count").intValue))

  private def countBy(field: String, limit: Int): Either[String, List[(String, Int)]] =
    run(List[Bson](
      Aggregates.group("$" + field, Accumulators.sum("count", 1)),
      Aggregates.sort(Sorts.descending("count")),
      Aggregates.limit(limit)
    )).map(counts)

  def byCuisine(limit: Int): Either[String, List[(String, Int)]] = countBy("cuisine", limit)

  def byBorough(): Either[String, List[(String, Int)]] = countBy("borough", 10)

  def avgScoreByCuisine(limit: Int): Either[String, List[(String, Double, Int)]] =
    run(List[Bson](
      Aggregates.unwind("$grades"),
      Aggregates.`match`(Filters.exists("grades.score")),
      Aggregates.group("$cuisine",
        Accumulators.avg("avgScore", "$grades.score"),
        Accumulators.sum("graded", 1)),
      Aggregates.`match`(Filters.gte("graded", 50)),
      Aggregates.sort(Sorts.descending("avgScore")),
      Aggregates.limit(limit)
    )).map(_.map(d => (label(d), d.getDouble("avgScore").doubleValue, d.getInteger("graded").intValue)))

  def topRated(limit: Int): Either[String, List[TopRated]] =
    run(List[Bson](
      Aggregates.unwind("$grades"),
      Aggregates.`match`(Filters.exists("grades.score")),
      Aggregates.group("$_id",
        Accumulators.first("name", "$name"),
        Accumulators.first("cuisine", "$cuisine"),
        Accumulators.first("borough", "$borough"),
        Accumulators.avg("avgScore", "$grades.score"),
        Accumulators.sum("inspections", 1)),
      Aggregates.`match`(Filters.gte("inspections", 3)),
      Aggregates.sort(Sorts.orderBy(Sorts.ascending("avgScore"), Sorts.ascending("name"))),
      Aggregates.limit(limit)
    )).map(_.map { d =>
      TopRated(
        name = d.getString("name"),
        cuisine = d.getString("cuisine"),
        borough = d.getString("borough"),
        avgScore = d.getDouble("avgScore").doubleValue,
        inspections = d.getInteger("inspections").intValue
      )
    })

  def gradeDistribution(): Either[String, List[(String, Int)]] =
    run(List[Bson](
      Aggregates.unwind("$grades"),
      Aggregates.group("$grades.grade", Accumulators.sum("count", 1)),
      Aggregates.sort(Sorts.descending("count"))
    )).map(counts)
}
