# 📚 Documentación del Código - Sistema de Chat Citofono

## 📋 Índice
1. [Arquitectura General](#arquitectura-general)
2. [Componentes Principales](#componentes-principales)
3. [Flujo de Datos](#flujo-de-datos)
4. [Parseo de Timestamps](#parseo-de-timestamps)
5. [Prevención de Duplicados](#prevención-de-duplicados)
6. [Sistema de Eventos](#sistema-de-eventos)
7. [Bugfixes Aplicados](#bugfixes-aplicados)

---

## 🏗️ Arquitectura General

El sistema de chat está construido con la siguiente arquitectura:

```
┌─────────────────────────────────────────────────────────────┐
│                    Android App (Citofono)                   │
├─────────────────────────────────────────────────────────────┤
│  UI Layer (Jetpack Compose)                                 │
│  ├─ ChatsActivity      : Lista de usuarios online           │
│  └─ MessageActivity    : Vista de conversación individual   │
├─────────────────────────────────────────────────────────────┤
│  ViewModel Layer                                             │
│  ├─ ChatViewModel      : Gestión de conexión y usuarios     │
│  └─ MessageViewModel   : Gestión de mensajes (SIN caché)    │
├─────────────────────────────────────────────────────────────┤
│  Client Layer                                                │
│  └─ InteractiveChatClient : Comunicación con BUS            │
└─────────────────────────────────────────────────────────────┘
                            ↕ TCP Socket
┌─────────────────────────────────────────────────────────────┐
│                    BUS de Mensajería (Python)               │
│  - Enrutamiento de mensajes                                 │
│  - Broadcast de eventos                                      │
│  - Gestión de clientes y servicios                          │
└─────────────────────────────────────────────────────────────┘
                            ↕
┌─────────────────────────────────────────────────────────────┐
│              Servicio de Mensajería (Python)                │
│  - Persistencia en MongoDB                                  │
│  - Lógica de negocio de mensajes                            │
│  - Gestión de conversaciones                                │
└─────────────────────────────────────────────────────────────┘
                            ↕
┌─────────────────────────────────────────────────────────────┐
│                      MongoDB Atlas                           │
│  - Base de datos de mensajes (fuente única de verdad)       │
│  - Historial persistente                                    │
└─────────────────────────────────────────────────────────────┘
```

---

## 🧩 Componentes Principales

### 1. **ChatInteractive.kt** - Cliente de Comunicación

**Propósito**: Cliente TCP que se conecta al BUS de mensajería y maneja toda la comunicación.

#### Funciones Clave:

##### `connect(): Boolean`
Establece conexión con el BUS:
1. Crea socket TCP al BUS
2. Envía mensaje REGISTER como "client"
3. Espera confirmación REGISTER_ACK
4. Inicia listener de eventos en thread daemon
5. Se conecta al servicio de mensajería
6. Hace broadcast de presencia

##### `clearEvents()`
**NUEVO**: Limpia la lista de eventos acumulados.
- Thread-safe con `eventsLock`
- Se llama al abrir un nuevo chat
- Evita procesar eventos de conversaciones anteriores

##### `getEventsCount(): Int`
**NUEVO**: Obtiene el número actual de eventos.
- Thread-safe
- Usado por MessageViewModel para rastrear eventos nuevos

##### `sendMessage(receiverId: String, message: String): Boolean`
Envía mensaje y persiste en MongoDB:
- NO agrega al historial local
- MessageViewModel maneja la UI

##### `getConversation(otherUserId: String): List<Map<String, Any>>`
Obtiene historial desde MongoDB:
- Últimos 50 mensajes
- Formato: `{from, to, text, ts}`
- Retorna lista cruda sin procesar

##### `listenEvents()`
Hilo daemon que escucha mensajes del BUS:
- Procesa BROADCAST y DIRECT
- Agrega a lista `events`
- Corre mientras `running == true`

---

### 2. **MessageViewModel.kt** - Gestión de Mensajes

**Propósito**: ViewModel para gestionar mensajes con **carga directa desde MongoDB**.

**Estrategia SIMPLIFICADA** (sin caché local):

```
┌─────────────────────────────────────────────────────────────┐
│ 1. AL ABRIR CHAT                                            │
├─────────────────────────────────────────────────────────────┤
│  ✓ Limpiar eventos acumulados previos                       │
│  ✓ Guardar índice actual (eventStartIndex)                  │
│  ✓ Solicitar TODOS los mensajes desde MongoDB               │
│  ✓ Parsear timestamps correctamente                         │
│  ✓ Ordenar por timestamp ascendente                         │
│  ✓ Marcar IDs como procesados                               │
│  ✓ Mostrar en UI                                            │
│  ✓ Iniciar listener (solo eventos NUEVOS)                   │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│ 2. ENVIAR MENSAJE                                           │
├─────────────────────────────────────────────────────────────┤
│  ✓ Crear con timestamp actual                               │
│  ✓ Verificar no duplicado                                   │
│  ✓ Agregar a lista y reordenar                              │
│  ✓ Mostrar inmediatamente (optimistic update)               │
│  ✓ Enviar al servidor → MongoDB                             │
│  ✓ Actualizar estado (sent/error)                           │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│ 3. RECIBIR MENSAJE EN TIEMPO REAL                           │
├─────────────────────────────────────────────────────────────┤
│  ✓ Parsear timestamp del evento                             │
│  ✓ Verificar no duplicado                                   │
│  ✓ Agregar y reordenar                                      │
│  ✓ Mostrar automáticamente                                  │
└─────────────────────────────────────────────────────────────┘
```

#### Funciones Clave:

##### `init{}`
Inicialización del ViewModel:
```kotlin
init {
    chatClient.currentChatSenderId = chatClient.userId
    chatClient.currentChatReceiverId = otherUserId
    
    // PASO 1: Limpiar eventos previos
    chatClient.clearEvents()
    
    // PASO 2: Guardar índice desde donde leeremos eventos nuevos
    eventStartIndex = chatClient.getEventsCount()
    
    // PASO 3: Cargar todos los mensajes desde MongoDB
    loadAllMessagesFromDatabase()
    
    // PASO 4: Iniciar listener (solo eventos DESPUÉS del índice)
    startRealtimeMessageListener()
}
```

##### `loadAllMessagesFromDatabase()`
Carga TODOS los mensajes desde MongoDB:
1. Solicita mensajes al servicio vía BUS
2. **Parsea timestamps** con `parseTimestamp()`
3. Convierte a `ChatMessage`
4. **Ordena por timestamp ascendente**
5. Marca IDs como procesados
6. Actualiza UI

##### `parseTimestamp(ts: String): Long`
**CRÍTICO**: Parsea timestamps de MongoDB.

Formato recibido: `"2025-10-21T00:33:37.567000"` (sin 'Z', 6 dígitos)

```kotlin
private fun parseTimestamp(ts: String): Long {
    // Truncar microsegundos (6) a milisegundos (3)
    val truncatedTs = if (ts.contains(".")) {
        val parts = ts.split(".")
        val fractionalPart = parts[1]
        if (fractionalPart.length > 3) {
            "${parts[0]}.${fractionalPart.substring(0, 3)}"
        } else {
            ts
        }
    } else {
        ts
    }
    
    // Parsear sin 'Z' en el formato
    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US)
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    return sdf.parse(truncatedTs)?.time ?: System.currentTimeMillis()
}
```

##### `parseTimestampFromEvent(ts: String): Long`
**CRÍTICO**: Parsea timestamps de eventos en tiempo real.

Formato recibido: `"2025-10-20T21:33:32.692167-03:00"` (con zona horaria)

```kotlin
private fun parseTimestampFromEvent(ts: String): Long {
    // 1. Extraer zona horaria y truncar microsegundos
    var processedTs = ts
    if (ts.contains(".")) {
        val parts = ts.split(".")
        val fractionalAndZone = parts[1]
        
        // Regex para zona: (+/-)HH:MM o Z
        val zoneMatch = Regex("([+-]\\d{2}:\\d{2}|Z)").find(fractionalAndZone)
        val zone = zoneMatch?.value ?: ""
        val fractional = fractionalAndZone.replace(zone, "")
        
        // Truncar a 3 dígitos
        val truncatedFractional = if (fractional.length > 3) {
            fractional.substring(0, 3)
        } else {
            fractional
        }
        
        processedTs = "${parts[0]}.${truncatedFractional}${zone}"
    }

    // 2. Intentar múltiples formatos
    try {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
        return sdf.parse(processedTs)?.time ?: System.currentTimeMillis()
    } catch (e: Exception) {
        // Intentar otros formatos...
    }
}
```

##### `sendMessage(text: String)`
Envío con optimistic update:
```kotlin
fun sendMessage(text: String) {
    val timestamp = System.currentTimeMillis()
    val messageId = generateMessageId(chatClient.userId, timestamp, text)
    
    // Verificar duplicados
    if (processedMessageIds.contains(messageId)) return
    processedMessageIds.add(messageId)
    
    // Crear mensaje
    val message = ChatMessage(...)
    
    // Agregar a UI inmediatamente y reordenar
    val currentMessages = _messages.value.toMutableList()
    currentMessages.add(message)
    _messages.value = currentMessages.sortedBy { it.timestamp }
    
    // Enviar al servidor en background
    withContext(Dispatchers.IO) {
        chatClient.sendMessage(otherUserId, text)
    }
}
```

##### `startRealtimeMessageListener()`
Escucha solo eventos NUEVOS:
```kotlin
private fun startRealtimeMessageListener() {
    var lastProcessedIndex = eventStartIndex  // Desde donde limpiamos
    
    while (true) {
        delay(300)
        
        val currentEventsSize = chatClient.getEventsCount()
        
        // Solo procesar eventos NUEVOS
        if (currentEventsSize > lastProcessedIndex) {
            val newEvents = chatClient.events.drop(lastProcessedIndex)
            
            newEvents.forEach { eventMap ->
                when (event) {
                    "new_message" -> {
                        // Parsear timestamp del evento
                        val timestamp = parseTimestampFromEvent(timestampStr)
                        
                        // Solo del otro usuario
                        if (from == otherUserId) {
                            val messageId = generateMessageId(from, timestamp, text)
                            
                            // Verificar duplicados
                            if (!processedMessageIds.contains(messageId)) {
                                val newMessage = ChatMessage(...)
                                processedMessageIds.add(messageId)
                                
                                // Agregar y reordenar
                                val currentMessages = _messages.value.toMutableList()
                                currentMessages.add(newMessage)
                                _messages.value = currentMessages.sortedBy { it.timestamp }
                            }
                        }
                    }
                }
            }
            
            lastProcessedIndex = currentEventsSize
        }
    }
}
```

##### `generateMessageId(from: String, timestamp: Long, text: String): String`
Genera ID único y consistente:
```kotlin
private fun generateMessageId(from: String, timestamp: Long, text: String): String {
    val textHash = text.hashCode().toString().replace("-", "n")
    return "${from}_${timestamp}_${textHash}"
}
```

---

## 🎯 Parseo de Timestamps

### Problema Original
MongoDB y eventos en tiempo real usan formatos diferentes con **microsegundos (6 dígitos)** en lugar de milisegundos (3 dígitos).

### Solución Implementada

#### Timestamps de MongoDB
- **Formato**: `"2025-10-21T00:33:37.567000"` (sin 'Z')
- **Parser**: `SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US)`
- **Proceso**: Truncar 6 dígitos → 3 dígitos antes de parsear

#### Timestamps de Eventos
- **Formato**: `"2025-10-20T21:33:32.692167-03:00"` (con zona horaria)
- **Parser**: `SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)`
- **Proceso**: 
  1. Extraer zona horaria con Regex
  2. Truncar microsegundos
  3. Reconstruir con zona
  4. Parsear con múltiples intentos

---

## 🔒 Prevención de Duplicados

### Estrategia Multi-Capa

#### 1. Set de IDs Procesados
```kotlin
private val processedMessageIds = mutableSetOf<String>()
```

#### 2. IDs Únicos y Consistentes
```kotlin
val messageId = "${from}_${timestamp}_${textHash}"
```

#### 3. Verificación en Cada Punto de Entrada
- Al cargar desde MongoDB
- Al enviar mensaje
- Al recibir evento en tiempo real

#### 4. Limpieza de Eventos
- `clearEvents()` al abrir chat
- `eventStartIndex` para rastrear inicio
- Solo procesar eventos NUEVOS

---

## 🔄 Sistema de Eventos

### Control de Índices

```kotlin
// Al inicializar
chatClient.clearEvents()  // Limpiar eventos viejos
eventStartIndex = chatClient.getEventsCount()  // Guardar índice actual

// En el listener
var lastProcessedIndex = eventStartIndex

while (true) {
    val currentEventsSize = chatClient.getEventsCount()
    
    if (currentEventsSize > lastProcessedIndex) {
        val newEvents = chatClient.events.drop(lastProcessedIndex)
        // Procesar solo eventos nuevos...
        lastProcessedIndex = currentEventsSize
    }
}
```

### Thread Safety
```kotlin
private val eventsLock = ReentrantLock()

fun clearEvents() {
    eventsLock.lock()
    try {
        events.clear()
    } finally {
        eventsLock.unlock()
    }
}
```

---

## 📊 Orden de Mensajes

### Regla de Oro
**SIEMPRE ordenar por timestamp ascendente** (más antiguo primero)

```kotlin
val sortedMessages = messages.sortedBy { it.timestamp }
```

### Visualización en UI
```
LazyColumn (sin reverseLayout)
├─ [0] Mensaje más antiguo    ← ARRIBA
├─ [1] Mensaje 2
├─ [2] Mensaje 3
└─ [N] Mensaje más reciente   ← ABAJO
```

### Auto-scroll
```kotlin
LaunchedEffect(messages.size) {
    if (messages.isNotEmpty()) {
        listState.animateScrollToItem(messages.size - 1)  // Ir al último
    }
}
```

---

## 🐛 Bugfixes Aplicados

### 1. Timestamps Mal Parseados
- **Antes**: Formato con 'Z' esperado, MongoDB sin 'Z' → fallo
- **Después**: Parser flexible que maneja ambos formatos

### 2. Mensajes Duplicados
- **Antes**: Eventos acumulados sin limpiar
- **Después**: `clearEvents()` + control de índices

### 3. Mensajes Desordenados
- **Antes**: Timestamps incorrectos → orden aleatorio
- **Después**: Parseo correcto + orden consistente

### 4. Eventos Reprocesados
- **Antes**: Procesaba TODOS los eventos cada vez
- **Después**: Solo eventos NUEVOS desde `eventStartIndex`

---

## 📁 Archivos Principales

1. **`ChatInteractive.kt`** (700+ líneas)
   - Cliente de comunicación con BUS
   - Sistema de eventos con locks
   - Funciones de limpieza

2. **`MessageViewModel.kt`** (450+ líneas)
   - Gestión de mensajes
   - Parsers de timestamps
   - Sistema anti-duplicados

3. **`MessageActivity.kt`** (400+ líneas)
   - UI con Jetpack Compose
   - LazyColumn para mensajes
   - Auto-scroll inteligente

4. **`ChatViewModel.kt`** (350+ líneas)
   - Gestión de conexión
   - Lista de usuarios online
   - Tareas periódicas

---

## 🚀 Última Actualización
**2025-10-20** - Sistema completamente funcional sin duplicados ni desorden
