import com.mongodb.client.{MongoClient, MongoCollection}
import com.mongodb.client.model.{Filters, Updates}
import org.bson.Document
import org.bson.conversions.Bson
import java.util.regex.Pattern
import scala.jdk.CollectionConverters._
import scala.util.Try

trait RestaurantStore {
  def add(r: Restaurant): Either[String, Unit]
  def findById(id: String): Option[Restaurant]
  def list(limit: Int): List[Restaurant]
  def updateField(id: String, field: String, value: String): Either[String, Boolean]
  def delete(id: String): Either[String, Boolean]

  def searchByName(text: String, limit: Int): Either[String, List[Restaurant]]
  def searchByCuisine(text: String, limit: Int): Either[String, List[Restaurant]]
  def searchByBorough(text: String, limit: Int): Either[String, List[Restaurant]]
  def searchByZipcode(zip: String, limit: Int): Either[String, List[Restaurant]]
  def searchByMinScore(min: Int, limit: Int): Either[String, List[Restaurant]]
}

class MongoRestaurantStore(client: MongoClient) extends RestaurantStore {

  private val collection: MongoCollection[Document] =
    client.getDatabase("sample_restaurants").getCollection("restaurants")

  private val fieldPaths: Map[String, String] = Map(
    "name" -> "name",
    "cuisine" -> "cuisine",
    "borough" -> "borough",
    "zipcode" -> "address.zipcode"
  )

  private def safely[A](action: => A): Either[String, A] =
    Try(action).toEither.left.map(e => s"Database error: ${e.getMessage}")

  private def containsCI(field: String, text: String): Bson =
    Filters.regex(field, Pattern.compile(Pattern.quote(text.trim), Pattern.CASE_INSENSITIVE))

  private def exactCI(field: String, text: String): Bson =
    Filters.regex(field, Pattern.compile("^" + Pattern.quote(text.trim) + "$", Pattern.CASE_INSENSITIVE))

  private def query(filter: Bson, limit: Int): Either[String, List[Restaurant]] =
    safely(collection.find(filter).limit(limit).asScala.toList.flatMap(Restaurant.fromDocument))

  def add(r: Restaurant): Either[String, Unit] =
    safely(collection.countDocuments(Filters.eq("restaurant_id", r.restaurantId))).flatMap {
      case n if n > 0 => Left(s"Restaurant ID ${r.restaurantId} already exists.")
      case _          => safely(collection.insertOne(r.toDocument)).map(_ => ())
    }

  def findById(id: String): Option[Restaurant] =
    Option(collection.find(Filters.eq("restaurant_id", id)).first())
      .flatMap(Restaurant.fromDocument)

  def list(limit: Int): List[Restaurant] =
    collection.find().limit(limit).asScala.toList.flatMap(Restaurant.fromDocument)

  def updateField(id: String, field: String, value: String): Either[String, Boolean] =
    fieldPaths.get(field) match {
      case None => Left(s"Unknown field: $field")
      case Some(path) =>
        safely(
          collection.updateOne(Filters.eq("restaurant_id", id), Updates.set(path, value)).getMatchedCount > 0
        )
    }

  def delete(id: String): Either[String, Boolean] =
    safely(collection.deleteOne(Filters.eq("restaurant_id", id)).getDeletedCount > 0)

  def searchByName(text: String, limit: Int): Either[String, List[Restaurant]] =
    query(containsCI("name", text), limit)

  def searchByCuisine(text: String, limit: Int): Either[String, List[Restaurant]] =
    query(exactCI("cuisine", text), limit)

  def searchByBorough(text: String, limit: Int): Either[String, List[Restaurant]] =
    query(exactCI("borough", text), limit)

  def searchByZipcode(zip: String, limit: Int): Either[String, List[Restaurant]] =
    query(Filters.eq("address.zipcode", zip.trim), limit)

  def searchByMinScore(min: Int, limit: Int): Either[String, List[Restaurant]] =
    query(Filters.elemMatch("grades", Filters.gte("score", min)), limit)
}
