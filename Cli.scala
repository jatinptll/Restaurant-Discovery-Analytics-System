import java.util.Date
import scala.annotation.tailrec
import scala.io.StdIn
import scala.util.Try

class Cli(store: RestaurantStore, analytics: Analytics, indexes: IndexManager) {

  private val Limit = 10

  private val menuText: String =
    """
      |============================================================
      |                        MAIN MENU
      |============================================================
      |1. Add Restaurant
      |2. View Restaurants
      |3. Search / Filter Restaurants
      |4. Update Restaurant
      |5. Delete Restaurant
      |6. Restaurant Analytics
      |7. Create Indexes
      |8. View Index Information
      |9. Exit
      |============================================================""".stripMargin

  private def ask(label: String): String = {
    val line = StdIn.readLine(s"$label: ")
    if (line == null) {
      println("\nInput closed. Exiting.")
      sys.exit(0)
    } else line.trim
  }

  private def askInt(label: String): Option[Int] = Try(ask(label).toInt).toOption

  private def header(title: String): Unit = println(s"\n========== $title ==========")

  private def printRestaurant(r: Restaurant): Unit = {
    val avg = r.averageScore.map(a => "%.1f".format(a)).getOrElse("n/a")
    val address = List(r.building, r.street).filter(_.nonEmpty).mkString(" ")
    println(s"ID       : ${r.restaurantId}")
    println(s"Name     : ${r.name}")
    println(s"Cuisine  : ${r.cuisine}")
    println(s"Borough  : ${r.borough}")
    println(s"ZIP Code : ${r.zipcode}")
    if (address.nonEmpty) println(s"Address  : $address")
    println(s"Avg score: $avg")
    println("-" * 60)
  }

  private def printResults(result: Either[String, List[Restaurant]]): Unit =
    result match {
      case Left(err)  => println(err)
      case Right(Nil) => println("No restaurants found.")
      case Right(rs) =>
        println(s"\nFound ${rs.size} restaurant(s) (showing up to $Limit).")
        rs.foreach(printRestaurant)
    }

  private def showRows[A](title: String, result: Either[String, List[A]])(line: A => String): Unit = {
    header(title)
    result match {
      case Left(err)   => println(err)
      case Right(rows) => rows.foreach(r => println(line(r)))
    }
  }

  private def gradeFor(score: Int): String = score match {
    case s if s <= 13 => "A"
    case s if s <= 27 => "B"
    case _            => "C"
  }

  private def addRestaurant(): Unit = {
    header("ADD RESTAURANT")
    val id = ask("Enter Restaurant ID")
    val name = ask("Enter Restaurant Name")
    val cuisine = ask("Enter Cuisine")
    val borough = ask("Enter Borough")
    val zip = ask("Enter ZIP Code")
    val scoreOpt = askInt("Enter Score (whole number)")
    val required = List(id, name, cuisine, borough, zip)

    (required.forall(_.nonEmpty), scoreOpt) match {
      case (false, _) => println("All fields are required. Nothing was added.")
      case (_, None)  => println("Score must be a whole number. Nothing was added.")
      case (true, Some(score)) =>
        val r = Restaurant(
          restaurantId = id,
          name = name,
          cuisine = cuisine,
          borough = borough,
          zipcode = zip,
          building = "",
          street = "",
          grades = List(Grade(Some(new Date()), gradeFor(score), Some(score)))
        )
        store.add(r) match {
          case Right(_)  => println("Restaurant added successfully!")
          case Left(err) => println(err)
        }
    }
  }

  private def viewRestaurants(): Unit = {
    header("VIEW RESTAURANTS")
    val n = askInt("How many to show (1-50)").filter(x => x >= 1 && x <= 50).getOrElse(10)
    store.list(n).foreach(printRestaurant)
  }

  private def searchMenu(): Unit = {
    header("SEARCH RESTAURANTS")
    println("1. Search by Restaurant Name")
    println("2. Search by Cuisine")
    println("3. Search by Borough")
    println("4. Search by ZIP Code")
    println("5. Search by Minimum Score")
    println("6. Back")
    ask("Enter choice") match {
      case "1" => printResults(store.searchByName(ask("Enter restaurant name"), Limit))
      case "2" => printResults(store.searchByCuisine(ask("Enter cuisine"), Limit))
      case "3" => printResults(store.searchByBorough(ask("Enter borough"), Limit))
      case "4" => printResults(store.searchByZipcode(ask("Enter ZIP code"), Limit))
      case "5" =>
        askInt("Enter minimum score") match {
          case Some(m) => printResults(store.searchByMinScore(m, Limit))
          case None    => println("Please enter a whole number.")
        }
      case "6" => ()
      case _   => println("Invalid choice.")
    }
  }

  private def updateRestaurant(): Unit = {
    header("UPDATE RESTAURANT")
    val id = ask("Enter Restaurant ID to update")
    store.findById(id) match {
      case None => println(s"No restaurant found with ID $id.")
      case Some(r) =>
        println("\nRestaurant found:")
        printRestaurant(r)
        println("What do you want to update?")
        println("1. Restaurant Name")
        println("2. Cuisine")
        println("3. Borough")
        println("4. ZIP Code")
        println("5. Cancel")
        val field = ask("Enter choice") match {
          case "1" => Some("name")
          case "2" => Some("cuisine")
          case "3" => Some("borough")
          case "4" => Some("zipcode")
          case _   => None
        }
        field.foreach { f =>
          val value = ask(s"Enter new $f")
          if (value.isEmpty) println("Value cannot be empty.")
          else
            store.updateField(id, f, value) match {
              case Right(true)  => println(s"$f updated successfully.")
              case Right(false) => println("Restaurant not found.")
              case Left(err)    => println(err)
            }
        }
    }
  }

  private def deleteRestaurant(): Unit = {
    header("DELETE RESTAURANT")
    val id = ask("Enter Restaurant ID to delete")
    store.findById(id) match {
      case None => println(s"No restaurant found with ID $id.")
      case Some(r) =>
        printRestaurant(r)
        if (ask("Type y to confirm delete").equalsIgnoreCase("y"))
          store.delete(id) match {
            case Right(true)  => println("Restaurant deleted successfully.")
            case Right(false) => println("Nothing was deleted.")
            case Left(err)    => println(err)
          }
        else println("Delete cancelled.")
    }
  }

  private def analyticsMenu(): Unit = {
    header("RESTAURANT ANALYTICS")
    println("1. Restaurants by Cuisine")
    println("2. Restaurants by Borough")
    println("3. Average Score by Cuisine")
    println("4. Top Rated Restaurants")
    println("5. Grade Distribution")
    println("6. Back")
    ask("Enter choice") match {
      case "1" =>
        showRows("RESTAURANTS BY CUISINE (top 15)", analytics.byCuisine(15)) { case (k, n) =>
          f"$k%-35s $n%6d"
        }
      case "2" =>
        showRows("RESTAURANTS BY BOROUGH", analytics.byBorough()) { case (k, n) =>
          f"$k%-35s $n%6d"
        }
      case "3" =>
        showRows("AVERAGE SCORE BY CUISINE (top 10, min 50 grades)", analytics.avgScoreByCuisine(10)) {
          case (k, avg, n) => f"$k%-35s $avg%6.2f  ($n grades)"
        }
      case "4" =>
        showRows("TOP RATED RESTAURANTS (lowest avg score)", analytics.topRated(10)) { r =>
          f"${r.name}%-32s ${r.cuisine}%-18s ${r.borough}%-14s ${r.avgScore}%5.2f (${r.inspections} inspections)"
        }
      case "5" =>
        showRows("GRADE DISTRIBUTION", analytics.gradeDistribution()) { case (k, n) =>
          f"Grade $k%-18s $n%6d"
        }
      case "6" => ()
      case _   => println("Invalid choice.")
    }
  }

  private def createIndexes(): Unit = {
    header("CREATE INDEXES")
    indexes.createAll() match {
      case Left(err)    => println(err)
      case Right(names) => names.foreach(n => println(s"Created/exists: $n"))
    }
  }

  private def indexInfo(): Unit = {
    header("INDEX INFORMATION")
    val all = indexes.listAll()
    println(s"Total indexes: ${all.size}")
    all.foreach(println)
    println("\nQuery: zipcode = 11225")
    println("  " + indexes.explainIndexUsed(indexes.zipcodeFilter("11225")))
    println("\nQuery: cuisine = Italian AND borough = Queens")
    println("  " + indexes.explainIndexUsed(indexes.cuisineBoroughFilter("Italian", "Queens")))
  }

  private def dispatch(choice: String): Unit =
    Try(choice match {
      case "1" => addRestaurant()
      case "2" => viewRestaurants()
      case "3" => searchMenu()
      case "4" => updateRestaurant()
      case "5" => deleteRestaurant()
      case "6" => analyticsMenu()
      case "7" => createIndexes()
      case "8" => indexInfo()
      case _   => println("Invalid choice. Please enter a number from 1 to 9.")
    }).failed.foreach(e => println(s"Something went wrong: ${e.getMessage}"))

  @tailrec
  final def run(): Unit = {
    println(menuText)
    val choice = ask("\nEnter your choice")
    if (choice == "9") println("Goodbye!")
    else {
      dispatch(choice)
      run()
    }
  }
}
