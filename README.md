# 🚀 MiniRedis Enterprise

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-blue.svg" alt="Java 21">
  <img src="https://img.shields.io/badge/Status-Stable-green.svg" alt="Status">
  <img src="https://img.shields.io/badge/Security-Hardened-red.svg" alt="Security">
</p>

---

## 📖 Descripción
**MiniRedis Enterprise** es un servidor de almacenamiento en memoria (Key-Value Store) diseñado desde cero en Java. Este proyecto combina la eficiencia de una estructura de datos en RAM con la seguridad de un entorno blindado contra inyecciones y ataques de denegación de servicio (DoS).

## ✨ Características Principales
* **🛠️ Protocolo Robusto:** Parser personalizado que soporta cadenas con espacios, comillas y caracteres especiales.
* **🛡️ Seguridad de Nivel Pro:**
    * Autenticación (AUTH) obligatoria.
    * Filtrado de comandos maliciosos y límites de tamaño (DoS Protection).
    * Manejo de excepciones controlado para evitar fugas de información.
* **💾 Persistencia:** Restauración automática de datos desde el disco tras cada reinicio.
* **⚡ Concurrencia:** Arquitectura basada en sockets para comunicación en tiempo real.

## 📋 Comandos Disponibles
| Comando | Descripción |
| :--- | :--- |
| `AUTH <pass>` | Autenticación maestra. |
| `SET <k> <v> [EX <s>]` | Guardar valor (con TTL opcional). |
| `GET <k>` | Obtener valor almacenado. |
| `HSET <k> <f> <v>` | Almacenamiento en estructuras Hash. |
| `PUBLISH <c> <m>` | Publicación de mensajes en canales. |
| `SUBSCRIBE <c>` | Suscripción a canales en tiempo real. |

## 🚀 Instalación y Uso
1. **Clonar el repositorio:**
   ```bash
   git clone [https://github.com/tu_usuario/mini-redis-enterprise.git](https://github.com/tu_usuario/mini-redis-enterprise.git)