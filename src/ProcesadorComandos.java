import java.io.PrintWriter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProcesadorComandos {

    // Contexto que guarda el estado de seguridad de CADA cliente conectado
    public static class SesionCliente {
        private boolean autenticado = false;
        private final PrintWriter salida;

        public SesionCliente(PrintWriter salida) {
            this.salida = salida;
        }

        public boolean isAutenticado() {
            return autenticado;
        }

        public void setAutenticado(boolean autenticado) {
            this.autenticado = autenticado;
        }

        public PrintWriter getSalida() {
            return salida;
        }
    }

    // Interfaz funcional para el Patrón Comando
    @FunctionalInterface
    interface InterfazComando {
        String ejecutar(String[] args, MotorMemoria motor, SesionCliente sesion);
    }

    private final MotorMemoria motor;
    private final Map<String, InterfazComando> mapaComandos = new HashMap<>();
    private final String PASSWORD_MAESTRA = "admin123";

    public ProcesadorComandos(MotorMemoria motor) {
        this.motor = motor;
        registrarComandos();
    }

    private void registrarComandos() {
        // Comando AUTH (Seguridad)
        mapaComandos.put("AUTH", (args, m, s) -> {
            if (args.length < 2) return "ERR sintaxis: AUTH <password>";
            // Sanitización extrema por si netcat arrastra caracteres invisibles de red
            String passRecibido = args[1].trim().replaceAll("[\\r\\n]", "");

            // Esto se imprimirá en la consola de IntelliJ para que audites qué está llegando
            System.out.println("[DEBUG AUTH] Esperado: '" + PASSWORD_MAESTRA + "' (" + PASSWORD_MAESTRA.length() + " chars) | Recibido: '" + passRecibido + "' (" + passRecibido.length() + " chars)");

            if (PASSWORD_MAESTRA.equals(passRecibido)) {
                s.setAutenticado(true);
                return "OK (Autenticado con éxito)";
            }
            return "ERR Contraseña incorrecta";
        });

        // Comando PING
        mapaComandos.put("PING", (args, m, s) -> "PONG");

        // Comando SET
        mapaComandos.put("SET", (args, m, s) -> {
            if (args.length < 3) return "ERR sintaxis: SET <clave> <valor> [EX segundos]";
            if (args.length >= 5 && "EX".equalsIgnoreCase(args[3])) {
                try {
                    m.guardarConTTL(args[1], args[2], Integer.parseInt(args[4]));
                    return "OK";
                } catch (NumberFormatException e) {
                    return "ERR tiempo inválido";
                }
            }
            m.guardarConTTL(args[1], args[2], null);
            return "OK";
        });

        // Comando GET
        mapaComandos.put("GET", (args, m, s) -> {
            if (args.length < 2) return "ERR sintaxis: GET <clave>";
            String val = m.obtener(args[1]);
            return val != null ? val : "(nil)";
        });

        // Comando LPUSH
        mapaComandos.put("LPUSH", (args, m, s) -> {
            if (args.length < 3) return "ERR sintaxis: LPUSH <clave> <elemento>";
            m.lpush(args[1], args[2]);
            return "OK";
        });

        // Comando LRANGE
        mapaComandos.put("LRANGE", (args, m, s) -> {
            if (args.length < 4) return "ERR sintaxis: LRANGE <clave> <inicio> <fin>";
            try {
                List<String> lista = m.lrange(args[1], Integer.parseInt(args[2]), Integer.parseInt(args[3]));
                if (lista == null) return "(nil)";
                if (lista.isEmpty()) return "(empty list)";
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < lista.size(); i++)
                    sb.append(i + 1).append(") \"").append(lista.get(i)).append("\"\n");
                return sb.toString().trim();
            } catch (NumberFormatException e) {
                return "ERR índices inválidos";
            }
        });

        // Comando HSET (Hashes)
        mapaComandos.put("HSET", (args, m, s) -> {
            if (args.length < 4) return "ERR sintaxis: HSET <clave> <campo> <valor>";
            m.hset(args[1], args[2], args[3]);
            return "OK";
        });

        // Comando HGET (Hashes)
        mapaComandos.put("HGET", (args, m, s) -> {
            if (args.length < 3) return "ERR sintaxis: HGET <clave> <campo>";
            String val = m.hget(args[1], args[2]);
            return val != null ? val : "(nil)";
        });

        // Comando SUBSCRIBE (Pub/Sub)
        mapaComandos.put("SUBSCRIBE", (args, m, s) -> {
            if (args.length < 2) return "ERR sintaxis: SUBSCRIBE <canal>";
            m.suscribir(args[1], s.getSalida());
            return "+OK Suscrito al canal '" + args[1] + "'. Esperando publicaciones...";
        });

        // Comando PUBLISH (Pub/Sub)
        mapaComandos.put("PUBLISH", (args, m, s) -> {
            if (args.length < 3) return "ERR sintaxis: PUBLISH <canal> <mensaje>";
            int receptores = m.publicar(args[1], args[2]);
            return "(integer) " + receptores;
        });

        // Comando DEL
        mapaComandos.put("DEL", (args, m, s) -> {
            if (args.length < 2) return "ERR sintaxis: DEL <clave>";
            return m.eliminar(args[1]) ? "(integer) 1" : "(integer) 0";
        });
    }
    private String limpiarComillas(String texto) {
        if (texto != null && texto.length() >= 2 && texto.startsWith("\"") && texto.endsWith("\"")) {
            return texto.substring(1, texto.length() - 1);
        }
        return texto != null ? texto : "";
    }

    public String ejecutar(String lineaEntrada, SesionCliente sesion) {
        try {
            if (lineaEntrada == null || lineaEntrada.trim().isEmpty()) {
                return "ERR comando vacio";
            }

            java.util.List<String> tokens = new java.util.ArrayList<>();
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\"[^\"]*\"|\\S+").matcher(lineaEntrada.trim());

            while (matcher.find()) {
                tokens.add(matcher.group());
            }

            if (tokens.isEmpty()) {
                return "ERR comando vacio";
            }

            String[] partes = tokens.toArray(new String[0]);
            String nombreComando = partes[0].toUpperCase();

            if ("QUIT".equals(nombreComando)) {
                return "BYE";
            }

            InterfazComando cmd = mapaComandos.get(nombreComando);
            if (cmd == null) {
                return "ERR comando desconocido '" + nombreComando + "'";
            }

            if (!sesion.isAutenticado() && !"AUTH".equals(nombreComando)) {
                return "NOAUTH Authentication required.";
            }

            for (int i = 0; i < partes.length; i++) {
                partes[i] = limpiarComillas(partes[i]);
            }

            return cmd.ejecutar(partes, motor, sesion);

        } catch (Exception e) {
            e.printStackTrace();
            return "ERR CRITICO INTERNO: " + e.toString();
        }
    }
}