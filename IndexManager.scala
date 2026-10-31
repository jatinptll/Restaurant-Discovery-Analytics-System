import com.mongodb.ExplainVerbosity
import com.mongodb.client.{MongoClient, MongoCollection}
import com.mongodb.client.model.{Filters, Indexes}
import org.bson.Document
import org.bson.conversions.Bson
import scala.jdk.CollectionConverters._
import scala.util.Try

class IndexManager(client: MongoClient) {

  private val collection: MongoCollection[Document] =
    client.getDatabase("sample_restaurants").getCollection("restaurants")

  def createAll(): Either[String, List[String]] =
    Try(
      List(
        collection.createIndex(Indexes.ascending("name")),
        collection.createIndex(Indexes.ascending("cuisine")),
        collection.createIndex(Indexes.ascending("borough")),
        collection.createIndex(Indexes.ascending("address.zipcode")),
        collection.createIndex(Indexes.ascending("cuisine", "borough"))
      )
    ).toEither.left.map(e => s"Index error: ${e.getMessage}")

  def listAll(): List[String] =
    collection.listIndexes().asScala.toList.map { d =>
      val keys = d.get("key", classOf[Document]).toJson
      s"${d.getString("name")}  ->  $keys"
    }

  private def findIndex(stage: Document): Option[String] =
    if ("IXSCAN" == stage.get("stage")) Option(stage.getString("indexName"))
    else {
      val children = List("inputStage", "queryPlan").flatMap(k => Option(stage.get(k, classOf[Document])))
      children.view.flatMap(findIndex).headOption
    }

  def explainIndexUsed(filter: Bson): String =
    Try {
      val plan = collection.find(filter).explain(ExplainVerbosity.QUERY_PLANNER)
      val winning = plan.get("queryPlanner", classOf[Document]).get("winningPlan", classOf[Document])
      findIndex(winning) match {
        case Some(name) => s"index used: $name"
        case None       => "no index used (collection scan)"
      }
    }.getOrElse("could not read query plan")

  def zipcodeFilter(zip: String): Bson = Filters.eq("address.zipcode", zip)

  def cuisineBoroughFilter(cuisine: String, borough: String): Bson =
    Filters.and(Filters.eq("cuisine", cuisine), Filters.eq("borough", borough))
}
