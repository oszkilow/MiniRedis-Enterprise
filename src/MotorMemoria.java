import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MotorMemoria {
    // Estructuras de datos concurrentes para cada tipo de dato
    private final Map<String, String> diccionario = new ConcurrentHashMap<>();
    private final Map<String, List<String>> listas = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> hashes = new ConcurrentHashMap<>();
    private final Map<String, Long> expiraciones = new ConcurrentHashMap<>();

    // Canales de Pub/Sub: Mapea un canal a los clientes que están escuchando
    private final Map<String, List<PrintWriter>> canalesPubSub = new ConcurrentHashMap<>();

    private final String ARCHIVO_DB = "snapshot.rdb";
    private final ScheduledExecutorService limpiadorGarantizado;

    public MotorMemoria() {
        cargarDeDisco();
        // Hilo guardián en segundo plano para limpiar llaves expiradas cada segundo
        this.limpiadorGarantizado = Executors.newSingleThreadScheduledExecutor();
        this.limpiadorGarantizado.scheduleAtFixedRate(this::evictLlavesExpiradas, 1, 1, TimeUnit.SECONDS);
    }

    // --- OPERACIONES DE STRINGS ---
    public synchronized void guardarConTTL(String clave, String valor, Integer segundosTTL) {
        limpiarLlaveDeOtrasEstructuras(clave);
        diccionario.put(clave, valor);
        if (segundosTTL != null && segundosTTL > 0) {
            expiraciones.put(clave, System.currentTimeMillis() + (segundosTTL * 1000L));
            System.out.println("[SET] " + clave + " => " + valor + " (Expira en " + segundosTTL + "s)");
        } else {
            expiraciones.remove(clave);
            System.out.println("[SET] " + clave + " => " + valor);
        }
        guardarADisco();
    }

    public String obtener(String clave) {
        if (haExpirado(clave)) {
            correrEliminacion(clave);
            return null;
        }
        String valor = diccionario.get(clave);
        System.out.println("[GET] " + clave + " => " + (valor != null ? valor : "(nil)"));
        return valor;
    }

    // --- OPERACIONES DE LISTAS ---
    public synchronized void lpush(String clave, String elemento) {
        limpiarLlaveDeOtrasEstructuras(clave);
        listas.computeIfAbsent(clave, k -> new CopyOnWriteArrayList<>()).add(0, elemento);
        System.out.println("[LPUSH] " + clave + " <= " + elemento);
        guardarADisco();
    }

    public List<String> lrange(String clave, int inicio, int fin) {
        if (!listas.containsKey(clave)) return null;
        List<String> completa = listas.get(clave);
        int size = completa.size();

        if (inicio < 0) inicio = 0;
        if (fin >= size) fin = size - 1;
        if (inicio > fin || inicio >= size) return new ArrayList<>();

        System.out.println("[LRANGE] Consultando rango en clave: " + clave);
        return completa.subList(inicio, fin + 1);
    }

    // --- OPERACIONES DE HASHES (Diccionarios Anidados) ---
    public synchronized void hset(String clave, String campo, String valor) {
        limpiarLlaveDeOtrasEstructuras(clave);
        hashes.computeIfAbsent(clave, k -> new ConcurrentHashMap<>()).put(campo, valor);
        System.out.println("[HSET] " + clave + " -> [" + campo + " : " + valor + "]");
        guardarADisco();
    }

    public String hget(String clave, String campo) {
        Map<String, String> mapaInterno = hashes.get(clave);
        if (mapaInterno == null) return null;
        String valor = mapaInterno.get(campo);
        System.out.println("[HGET] " + clave + ":" + campo + " => " + (valor != null ? valor : "(nil)"));
        return valor;
    }

    // --- OPERACIONES DE PUB/SUB (Mensajería) ---
    public void suscribir(String canal, PrintWriter salidaCliente) {
        canalesPubSub.computeIfAbsent(canal, k -> new CopyOnWriteArrayList<>()).add(salidaCliente);
        System.out.println("[PUB/SUB] Nuevo suscriptor en el canal: " + canal);
    }

    public int publicar(String canal, String mensaje) {
        List<PrintWriter> suscriptores = canalesPubSub.get(canal);
        if (suscriptores == null || suscriptores.isEmpty()) return 0;

        int receptores = 0;
        for (PrintWriter salida : suscriptores) {
            try {
                salida.println("\n[CANAL " + canal + "]: " + mensaje);
                receptores++;
            } catch (Exception e) {
                suscriptores.remove(salida); // Limpia automáticamente sockets cerrados
            }
        }
        System.out.println("[PUBLISH] Mensaje enviado a " + receptores + " clientes en canal '" + canal + "'");
        return receptores;
    }

    // --- ELIMINACIÓN Y LIMPIEZA ---
    public synchronized boolean eliminar(String clave) {
        return correrEliminacion(clave);
    }

    private boolean correrEliminacion(String clave) {
        expiraciones.remove(clave);
        boolean stringEliminado = diccionario.remove(clave) != null;
        boolean listaEliminada = listas.remove(clave) != null;
        boolean hashEliminado = hashes.remove(clave) != null;

        if (stringEliminado || listaEliminada || hashEliminado) {
            System.out.println("[DEL] " + clave + " => Eliminado de memoria");
            guardarADisco();
            return true;
        }
        return false;
    }

    private void limpiarLlaveDeOtrasEstructuras(String clave) {
        // Evita colisiones si una llave cambia de tipo (ej: de String a Lista)
        expiraciones.remove(clave);
        diccionario.remove(clave);
        listas.remove(clave);
        hashes.remove(clave);
    }

    private boolean haExpirado(String clave) {
        if (!expiraciones.containsKey(clave)) return false;
        return System.currentTimeMillis() > expiraciones.get(clave);
    }

    private void evictLlavesExpiradas() {
        long ahora = System.currentTimeMillis();
        expiraciones.forEach((clave, tiempo) -> {
            if (ahora > tiempo) {
                System.out.println("[GUARDIÁN] La clave '" + clave + "' ha caducado. Evicting...");
                correrEliminacion(clave);
            }
        });
    }

    // --- PERSISTENCIA EN DISCO ---
    private void guardarADisco() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(ARCHIVO_DB))) {
            // Guardar Cadenas simples
            diccionario.forEach((k, v) -> {
                if (!haExpirado(k)) writer.println("STR:" + k + "=" + v);
            });
            // Guardar Listas
            listas.forEach((k, v) -> writer.println("LIST:" + k + "=" + String.join(",", v)));
            // Guardar Estructuras Hash
            hashes.forEach((k, mapaInterno) -> mapaInterno.forEach((campo, v) ->
                    writer.println("HASH:" + k + ":" + campo + "=" + v)
            ));
        } catch (IOException e) {
            System.err.println("[ERROR PERSISTENCIA] " + e.getMessage());
        }
    }

    private void cargarDeDisco() {
        File archivo = new File(ARCHIVO_DB);
        if (!archivo.exists()) return;
        try (BufferedReader reader = new BufferedReader(new FileReader(archivo))) {
            String linea;
            while ((linea = reader.readLine()) != null) {
                if (linea.startsWith("STR:")) {
                    String[] p = linea.substring(4).split("=", 2);
                    if (p.length == 2) diccionario.put(p[0], p[1]);
                } else if (linea.startsWith("LIST:")) {
                    String[] p = linea.substring(5).split("=", 2);
                    if (p.length == 2) listas.put(p[0], new CopyOnWriteArrayList<>(p[1].split(",")));
                } else if (linea.startsWith("HASH:")) {
                    String[] p = linea.substring(5).split("=", 2);
                    if (p.length == 2) {
                        String[] m = p[0].split(":", 2);
                        if (m.length == 2) hashes.computeIfAbsent(m[0], k -> new ConcurrentHashMap<>()).put(m[1], p[1]);
                    }
                }
            }
            System.out.println("[PERSISTENCIA] Base de datos restaurada correctamente.");
        } catch (IOException e) {
            System.err.println("[ERROR PERSISTENCIA] " + e.getMessage());
        }
    }
}