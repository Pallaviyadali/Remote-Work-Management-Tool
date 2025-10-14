/*
RemoteWorkManagement.java

Single-file Java Remote Work Management system demonstrating:
 - MongoDB CRUD integration (sync driver)
 - DSA usage: PriorityQueue (task scheduling), LinkedList (history), Trie (employee name search), HashMap (assignments)
 - Simple CLI for adding employees, creating projects, creating tasks, assigning tasks, marking complete,
   listing/searching, and persisting to MongoDB.

Notes:
 - Replace MONGO_URI with your connection string.
 - Uses the MongoDB sync Java driver (com.mongodb.client.*) and org.bson.*
 - Intended for educational/demo purposes; production systems should use better error handling,
   authentication, input validation, and concurrency control.

To compile:
 javac -cp ".:mongodb-driver-sync-4.9.0.jar" RemoteWorkManagement.java
 To run:
 java -cp ".:mongodb-driver-sync-4.9.0.jar" RemoteWorkManagement
*/

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.PriorityBlockingQueue;

public class RemoteWorkManagement {

    // === CONFIG ===
    private static final String MONGO_URI = "mongodb://localhost:27017"; // <- change this
    private static final String DB_NAME = "remote_work_db";
    private static final String EMP_COLL = "employees";
    private static final String TASK_COLL = "tasks";
    private static final String PROJECT_COLL = "projects";

    // === IN-MEMORY DSA STRUCTURES ===
    // PriorityQueue for tasks by priority (higher priority first) and earliest due date
    private final PriorityQueue<Task> taskQueue = new PriorityQueue<>((a, b) -> {
        if (a.priority != b.priority) return Integer.compare(b.priority, a.priority);
        return Long.compare(a.dueEpoch, b.dueEpoch);
    });

    // Keep historical actions in a LinkedList
    private final LinkedList<String> history = new LinkedList<>();

    // Trie for employee name search
    private final Trie employeeTrie = new Trie();

    // Map employeeId -> list of assigned tasks (in-memory index)
    private final Map<String, List<String>> assignmentMap = new HashMap<>();

    // MongoDB objects
    private MongoClient mongoClient;
    private MongoDatabase database;
    private MongoCollection<Document> empColl;
    private MongoCollection<Document> taskColl;
    private MongoCollection<Document> projColl;

    public static void main(String[] args) {
        RemoteWorkManagement app = new RemoteWorkManagement();
        app.run();
    }

    private void run() {
        connectMongo();
        loadInMemoryStructures();

        Scanner sc = new Scanner(System.in);
        boolean running = true;
        while (running) {
            printMenu();
            System.out.print("Enter choice: ");
            String choice = sc.nextLine().trim();
            switch (choice) {
                case "1": addEmployee(sc); break;
                case "2": listEmployees(); break;
                case "3": createProject(sc); break;
                case "4": createTask(sc); break;
                case "5": assignTask(sc); break;
                case "6": listTasks(); break;
                case "7": completeTask(sc); break;
                case "8": searchEmployeeByName(sc); break;
                case "9": showHistory(); break;
                case "0": running = false; break;
                default: System.out.println("Unknown choice");
            }
        }

        close();
        System.out.println("Exited. Goodbye.");
    }

    private void printMenu() {
        System.out.println("\n=== Remote Work Management ===");
        System.out.println("1) Add Employee");
        System.out.println("2) List Employees");
        System.out.println("3) Create Project");
        System.out.println("4) Create Task");
        System.out.println("5) Assign Task to Employee");
        System.out.println("6) List Tasks");
        System.out.println("7) Mark Task Complete");
        System.out.println("8) Search Employee by Name (Trie)");
        System.out.println("9) Show Recent History");
        System.out.println("0) Exit");
    }

    private void connectMongo() {
        mongoClient = MongoClients.create(MONGO_URI);
        database = mongoClient.getDatabase(DB_NAME);
        empColl = database.getCollection(EMP_COLL);
        taskColl = database.getCollection(TASK_COLL);
        projColl = database.getCollection(PROJECT_COLL);

        System.out.println("Connected to MongoDB at " + MONGO_URI + ", DB: " + DB_NAME);
    }

    private void close() {
        if (mongoClient != null) {
            mongoClient.close();
        }
    }

    private void loadInMemoryStructures() {
        // load employees into trie and assignmentMap
        FindIterable<Document> emps = empColl.find();
        for (Document d : emps) {
            String id = d.getObjectId("_id").toHexString();
            String name = d.getString("name");
            if (name != null) employeeTrie.insert(name.toLowerCase(), id);
            assignmentMap.putIfAbsent(id, new ArrayList<>());
        }

        // load tasks into priority queue and assignmentMap
        FindIterable<Document> tasks = taskColl.find();
        for (Document t : tasks) {
            Task task = Task.fromDocument(t);
            taskQueue.offer(task);
            if (task.assignedToId != null) {
                assignmentMap.putIfAbsent(task.assignedToId, new ArrayList<>());
                assignmentMap.get(task.assignedToId).add(task._id);
            }
        }

        // load projects (not used heavily in-memory for demo)
        System.out.println("Loaded in-memory indexes: employees=" + assignmentMap.size() + ", tasks=" + taskQueue.size());
    }

    // ----------------- Employee -----------------
    private void addEmployee(Scanner sc) {
        System.out.print("Name: ");
        String name = sc.nextLine().trim();
        System.out.print("Email: ");
        String email = sc.nextLine().trim();

        Document d = new Document("name", name)
                .append("email", email)
                .append("createdAt", Date.from(Instant.now()));

        empColl.insertOne(d);
        String id = d.getObjectId("_id").toHexString();
        employeeTrie.insert(name.toLowerCase(), id);
        assignmentMap.putIfAbsent(id, new ArrayList<>());

        recordHistory("Added employee: " + name + " (" + id + ")");
        System.out.println("Employee added with id: " + id);
    }

    private void listEmployees() {
        FindIterable<Document> emps = empColl.find();
        System.out.println("\nEmployees:");
        for (Document d : emps) {
            System.out.println(prettyEmployee(d));
        }
    }

    private String prettyEmployee(Document d) {
        String id = d.getObjectId("_id").toHexString();
        String name = d.getString("name");
        String email = d.getString("email");
        Date c = d.getDate("createdAt");
        return String.format("%s | %s | %s | createdAt=%s", id, name, email, c);
    }

    // ----------------- Project -----------------
    private void createProject(Scanner sc) {
        System.out.print("Project name: ");
        String name = sc.nextLine().trim();
        System.out.print("Description: ");
        String desc = sc.nextLine().trim();

        Document d = new Document("name", name)
                .append("description", desc)
                .append("createdAt", Date.from(Instant.now()));
        projColl.insertOne(d);
        String id = d.getObjectId("_id").toHexString();
        recordHistory("Created project: " + name + " (" + id + ")");
        System.out.println("Project created with id: " + id);
    }

    // ----------------- Task -----------------
    private void createTask(Scanner sc) {
        System.out.print("Title: ");
        String title = sc.nextLine().trim();
        System.out.print("Details: ");
        String details = sc.nextLine().trim();
        System.out.print("Priority (1 low - 5 high): ");
        int priority = Integer.parseInt(sc.nextLine().trim());
        System.out.print("Due date epoch seconds (e.g. 1710000000) or 0 for none: ");
        long due = Long.parseLong(sc.nextLine().trim());

        Task task = new Task(title, details, priority, due == 0 ? Long.MAX_VALUE : due);
        Document d = task.toDocument();
        taskColl.insertOne(d);
        task._id = d.getObjectId("_id").toHexString();
        taskQueue.offer(task);

        recordHistory("Created task: " + title + " (" + task._id + ")");
        System.out.println("Task created with id: " + task._id);
    }

    private void assignTask(Scanner sc) {
        System.out.print("Task id: ");
        String taskId = sc.nextLine().trim();
        System.out.print("Employee id: ");
        String empId = sc.nextLine().trim();

        Document tDoc = taskColl.find(Filters.eq("_id", new ObjectId(taskId))).first();
        if (tDoc == null) {
            System.out.println("Task not found");
            return;
        }

        Document eDoc = empColl.find(Filters.eq("_id", new ObjectId(empId))).first();
        if (eDoc == null) {
            System.out.println("Employee not found");
            return;
        }

        taskColl.updateOne(Filters.eq("_id", new ObjectId(taskId)), new Document("$set", new Document("assignedToId", empId)));

        assignmentMap.putIfAbsent(empId, new ArrayList<>());
        assignmentMap.get(empId).add(taskId);

        // update in-memory taskQueue (simple approach: rebuild)
        rebuildTaskQueueFromDB();

        recordHistory("Assigned task " + taskId + " to employee " + empId);
        System.out.println("Assigned task.");
    }

    private void rebuildTaskQueueFromDB() {
        taskQueue.clear();
        FindIterable<Document> tasks = taskColl.find();
        for (Document t : tasks) {
            taskQueue.offer(Task.fromDocument(t));
        }
    }

    private void listTasks() {
        // show tasks sorted by priority/duedate (peek queue into array)
        List<Task> snapshot = new ArrayList<>(taskQueue);
        snapshot.sort((a, b) -> {
            if (a.priority != b.priority) return Integer.compare(b.priority, a.priority);
            return Long.compare(a.dueEpoch, b.dueEpoch);
        });

        System.out.println("\nTasks:");
        for (Task t : snapshot) {
            System.out.println(t);
        }
    }

    private void completeTask(Scanner sc) {
        System.out.print("Task id: ");
        String tid = sc.nextLine().trim();
        Document tDoc = taskColl.find(Filters.eq("_id", new ObjectId(tid))).first();
        if (tDoc == null) {
            System.out.println("Task not found");
            return;
        }
        taskColl.updateOne(Filters.eq("_id", new ObjectId(tid)), new Document("$set", new Document("status", "COMPLETED").append("completedAt", Date.from(Instant.now()))));

        // update assignment map and in-memory queue
        String assignedTo = tDoc.getString("assignedToId");
        if (assignedTo != null) {
            List<String> list = assignmentMap.get(assignedTo);
            if (list != null) list.remove(tid);
        }
        rebuildTaskQueueFromDB();

        recordHistory("Completed task: " + tid);
        System.out.println("Marked complete.");
    }

    private void searchEmployeeByName(Scanner sc) {
        System.out.print("Enter name prefix: ");
        String prefix = sc.nextLine().trim().toLowerCase();
        List<String> ids = employeeTrie.searchPrefix(prefix);
        if (ids.isEmpty()) {
            System.out.println("No employees found with that prefix.");
            return;
        }
        System.out.println("Matches:");
        for (String id : ids) {
            Document d = empColl.find(Filters.eq("_id", new ObjectId(id))).first();
            if (d != null) System.out.println(prettyEmployee(d));
        }
    }

    private void showHistory() {
        System.out.println("\nRecent History (latest first):");
        Iterator<String> it = history.descendingIterator();
        int i = 0;
        while (it.hasNext() && i < 20) {
            System.out.println(it.next());
            i++;
        }
    }

    private void recordHistory(String s) {
        history.add(Instant.now().toString() + " - " + s);
        if (history.size() > 1000) history.removeFirst();
    }

    // ===================== Helper data classes =====================
    static class Task {
        String _id;
        String title;
        String details;
        int priority;
        long dueEpoch; // epoch seconds
        String assignedToId;
        String status; // OPEN / COMPLETED
        Date createdAt;

        Task(String title, String details, int priority, long dueEpoch) {
            this.title = title;
            this.details = details;
            this.priority = priority;
            this.dueEpoch = dueEpoch;
            this.status = "OPEN";
            this.createdAt = Date.from(Instant.now());
        }

        Document toDocument() {
            Document d = new Document("title", title)
                    .append("details", details)
                    .append("priority", priority)
                    .append("dueEpoch", dueEpoch)
                    .append("status", status)
                    .append("createdAt", createdAt);
            if (assignedToId != null) d.append("assignedToId", assignedToId);
            return d;
        }

        static Task fromDocument(Document d) {
            String title = d.getString("title");
            String details = d.getString("details");
            int priority = d.getInteger("priority", 1);
            long dueEpoch = d.getLong("dueEpoch") == null ? Long.MAX_VALUE : d.getLong("dueEpoch");
            Task t = new Task(title, details, priority, dueEpoch);
            if (d.getObjectId("_id") != null) t._id = d.getObjectId("_id").toHexString();
            t.assignedToId = d.getString("assignedToId");
            t.status = d.getString("status") == null ? "OPEN" : d.getString("status");
            t.createdAt = d.getDate("createdAt");
            return t;
        }

        public String toString() {
            return String.format("%s | %s | pr=%d | due=%s | assignee=%s | status=%s",
                    _id, title, priority, dueEpoch == Long.MAX_VALUE ? "none" : String.valueOf(dueEpoch), assignedToId, status);
        }
    }

    // A tiny Trie implementation to index employee names -> id list
    static class Trie {
        static class Node {
            Map<Character, Node> children = new HashMap<>();
            List<String> ids = new ArrayList<>();
            boolean isEnd = false;
        }
        private final Node root = new Node();

        void insert(String word, String id) {
            Node cur = root;
            for (char c : word.toCharArray()) {
                cur.children.putIfAbsent(c, new Node());
                cur = cur.children.get(c);
                // keep id in prefix nodes so prefix search returns quickly; keep small set
                if (!cur.ids.contains(id)) cur.ids.add(id);
            }
            cur.isEnd = true;
            if (!cur.ids.contains(id)) cur.ids.add(id);
        }

        List<String> searchPrefix(String prefix) {
            Node cur = root;
            for (char c : prefix.toCharArray()) {
                if (!cur.children.containsKey(c)) return Collections.emptyList();
                cur = cur.children.get(c);
            }
            // return up to 50 ids
            List<String> result = new ArrayList<>(cur.ids);
            if (result.size() > 50) return result.subList(0, 50);
            return result;
        }
    }
}