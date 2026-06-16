/*
 * ==========================================================================
 * PROYECTO: MiniRedis Enterprise
 * AUTOR: Ozscar Fuentes
 * FECHA: Junio, 2026
 * DESCRIPCIÓN: Servidor de almacenamiento en memoria con persistencia,
 * seguridad robusta y soporte de protocolos TCP.
 * LICENCIA: MIT / Propiedad Intelectual
 * ==========================================================================
 */

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {
    private static final int PUERTO = 6379;

    public static void main(String[] args) {
        // Mostramos el banner antes de arrancar cualquier lógica
        mostrarBanner();

        MotorMemoria motor = new MotorMemoria();
        ProcesadorComandos procesador = new ProcesadorComandos(motor);
        ExecutorService poolHilos = Executors.newFixedThreadPool(15);

        System.out.println("[INFO] Configurando pool de hilos...");
        System.out.println("[INFO] Escuchando en el puerto: " + PUERTO);

        try (ServerSocket servidorSocket = new ServerSocket(PUERTO)) {
            while (true) {
                Socket clienteSocket = servidorSocket.accept();
                poolHilos.execute(() -> manejarCliente(clienteSocket, procesador));
            }
        } catch (Exception e) {
            System.err.println("[FATAL] Error en el servidor: " + e.getMessage());
        } finally {
            poolHilos.shutdown();
        }
    }

    private static void manejarCliente(Socket socket, ProcesadorComandos procesador) {
        try (Socket cliente = socket;
             BufferedReader entrada = new BufferedReader(new InputStreamReader(cliente.getInputStream()));
             PrintWriter salida = new PrintWriter(cliente.getOutputStream(), true)) {

            salida.println("+OK MiniRedis Enterprise Listo. Se requiere autenticación (AUTH).");

            // Creamos el estado de la sesión para ESTE hilo/cliente de red
            ProcesadorComandos.SesionCliente sesion = new ProcesadorComandos.SesionCliente(salida);

            String lineaCliente;
            while ((lineaCliente = entrada.readLine()) != null) {
                String respuesta = procesador.ejecutar(lineaCliente, sesion);
                salida.println(respuesta);
                if ("BYE".equals(respuesta)) break;
            }
        } catch (Exception e) {
            // Manejo silencioso de desconexiones
        }
    }

    private static void mostrarBanner() {
        System.out.println("******************************************************");
        System.out.println("* 🚀 MINIREDIS ENTERPRISE SERVER v1.0                *");
        System.out.println("* *");
        System.out.println("* Arquitecto: Ozscar Fuentes                         *");
        System.out.println("* Entorno: Java 21 LTS | Blindaje de Seguridad       *");
        System.out.println("******************************************************");
        System.out.println();
    }
}