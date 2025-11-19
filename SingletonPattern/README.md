# 📘 Singleton Pattern in Java

This repository demonstrates **industry-standard implementations** of the Singleton Pattern in Java, focusing on the two most robust and widely recommended approaches:

1. **Enum Singleton**
2. **Initialization-on-Demand Holder Idiom** – clean, lazy, thread-safe

Both implementations include improvements such as:

* constructor guards (reflection protection)
* serialization handling (`readResolve`)
* multi-threading identity checks
* clear demonstration via a `Driver` class

---

## 🔥 Why Singleton Pattern?

A Singleton ensures that **only one instance** of a class exists throughout the JVM lifecycle and provides a **global access point** to it.

This is useful for:

* configuration managers
* caches
* logging components
* connection factories
* service orchestrators

---

## 📂 Project Structure

```
.
├── SingletonUsingEnum.java
├── InitialiazerOnDemandIdiom.java
└── Driver.java
```

---

# 🟦 1. Enum Singleton (Best Practice)

### 📌 Key Features

* thread-safe by design
* resistant to reflection attacks
* Serialization-safe **automatically**
* simplest and most robust approach

---

# 🟩 2. Initialization-on-Demand Holder Idiom (Lazy, Fast, Clean)

### 📌 Key Features

* lazy initialization without synchronization
* JVM guarantees thread-safety
* constructor guard prevents reflection-based creation
* `readResolve()` ensures correct deserialization behavior

--- 

# 🧪 3. Driver Demonstration

### ✔ Features in `Driver.java`:

* accesses enum singleton and updates state
* checks instance identity (`==` and `equals`)
* verifies lazy initialization
* runs multi-threaded identity test
* demonstrates serialization round-trip
* attempts reflection attack

---

# 🛡 Which Singleton Should You Use?

| Pattern            | Pros                                                         | Cons                                        |
| ------------------ | ------------------------------------------------------------ | ------------------------------------------- |
| **Enum Singleton** | Safest, simplest, reflection-proof, auto serialization-safe  | Cannot be lazy-loaded directly              |
| **Holder Idiom**   | Lazy, clean, high-performance, supports complex construction | Needs constructor guard and `readResolve()` |

### In **Spring Boot**

You almost never write manual singleton patterns.
Spring beans are singleton by default and are managed by the container.

---
