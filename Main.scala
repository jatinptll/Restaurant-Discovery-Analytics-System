import com.mongodb.client.MongoClients
import org.bson.Document
import scala.util.{Failure, Try}

object Main {
  def main(args: Array[String]): Unit = {
    val uri = sys.env.getOrElse("MONGO_URI", {
      println("MONGO_URI is not set.")
      sys.exit(1)
    })
    val client = MongoClients.create(uri)
    try {
      println("\n============================================================")
      println("        RESTAURANT DISCOVERY & ANALYTICS SYSTEM")
      println("        Advanced Big Data Analytics | Scala + MongoDB")
      println("============================================================")

      Try(client.getDatabase("sample_restaurants").runCommand(new Document("ping", 1))) match {
        case Failure(e) =>
          println(s"Could not connect to MongoDB: ${e.getMessage}")
          sys.exit(1)
        case _ =>
          println("MongoDB connection successful!")
          println("Database   : sample_restaurants")
          println("Collection : restaurants")
      }

      new Cli(
        new MongoRestaurantStore(client),
        new Analytics(client),
        new IndexManager(client)
      ).run()
    } finally client.close()
  }
}
