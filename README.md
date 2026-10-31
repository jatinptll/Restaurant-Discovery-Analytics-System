# Restaurant Discovery & Analytics System

Advanced Big Data Analytics assignment: a menu-driven Scala CLI application backed by MongoDB, using the official `sample_restaurants` dataset.

**Name:** Jatin Patel
**Enrollment no.:** 23162121029
**Branch - Batch:** B.Tech CSE (BDA), 75

## Requirements

- Java 17 or newer
- [Scala CLI](https://scala-cli.virtuslab.org/) (`brew install scala-cli` on macOS)
- A MongoDB Atlas cluster with the sample dataset loaded (Atlas: **...** > **Load Sample Dataset**)

## Setup

1. In Atlas, load the sample dataset so that `sample_restaurants.restaurants` exists (about 25,000 documents).
2. In Atlas, allow your IP address under **Network Access** and create a database user with read and write access.
3. Copy your connection string from **Connect > Drivers**, fill in your user and password, and export it:

```bash
export MONGO_URI="mongodb+srv://<user>:<password>@<cluster>.mongodb.net/"
```

The connection string is read from the `MONGO_URI` environment variable, so no credentials are stored in the code.

## Run

From the project folder:

```bash
scala-cli run .
```

## Menu

1. Add Restaurant
2. View Restaurants
3. Search / Filter Restaurants (name, cuisine, borough, ZIP code, minimum score)
4. Update Restaurant (name, cuisine, borough, ZIP code)
5. Delete Restaurant (with confirmation)
6. Restaurant Analytics (5 aggregations)
7. Create Indexes
8. View Index Information (with `explain` showing which index each query uses)
9. Exit

## MongoDB features

- **CRUD:** insert, find, update and delete through the Scala MongoDB driver.
- **Indexes (5 plus the default `_id`):** `name`, `cuisine`, `borough`, `address.zipcode`, and the compound index `cuisine + borough`.
- **Aggregations:** restaurants by cuisine, restaurants by borough, average score by cuisine, top rated restaurants (lowest average inspection score), grade distribution.

## Scala concepts used

- Case classes (`Restaurant`, `Grade`, `TopRated`) and companion objects
- Trait (`RestaurantStore`) with an implementing class (`MongoRestaurantStore`)
- Encapsulation with private members and a separation of CLI, data access, analytics and index code
- `Option`, `Either`, `Try` and pattern matching for missing data and error handling
- Collections: `map`, `flatMap`, `filter`, `foreach`, `Map`, `List`

## Project structure

| File | Purpose |
| --- | --- |
| `project.scala` | Scala version and dependencies |
| `Main.scala` | Entry point and MongoDB connection check |
| `Restaurant.scala` | Data model and document conversion |
| `RestaurantStore.scala` | Trait and MongoDB implementation (CRUD and search) |
| `IndexManager.scala` | Index creation, listing and query plan checks |
| `Analytics.scala` | Aggregation pipelines |
| `Cli.scala` | Menu and user input handling |
