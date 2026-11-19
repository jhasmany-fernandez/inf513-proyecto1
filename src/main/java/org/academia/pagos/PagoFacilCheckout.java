package org.academia.pagos;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Genera el formulario de Pago Fácil Checkout (no usa /login de MasterQR). */
public final class PagoFacilCheckout {
    private final String comercio;
    private final String servicio;
    private final String callback;
    private final String retorno;
    private final String url;

    public PagoFacilCheckout(String commerceId, String tokenService, String callbackUrl, String returnUrl) {
        if (commerceId == null || commerceId.isBlank() || tokenService == null || tokenService.isBlank())
            throw new IllegalArgumentException("Configure Commerce ID y Token Service de Pago Fácil Checkout.");
        this.comercio = commerceId; this.servicio = tokenService;
        this.callback = callbackUrl == null ? "" : callbackUrl;
        this.retorno = returnUrl == null ? "" : returnUrl;
        this.url = "https://checkout.pagofacil.com.bo/es/pay";
    }

    public String datos(String email, String telefono, String pedidoId, BigDecimal monto, String detalle) {
        if (email == null || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw new IllegalArgumentException("Correo del cliente invalido.");
        if (telefono == null || !telefono.matches("[0-9]{7,15}")) throw new IllegalArgumentException("Telefono invalido.");
        if (pedidoId == null || !pedidoId.matches("[A-Za-z0-9._-]{1,120}")) throw new IllegalArgumentException("ID de pedido invalido.");
        if (monto == null || monto.signum() <= 0 || monto.scale() > 2) throw new IllegalArgumentException("El monto debe ser positivo y tener hasta dos decimales.");
        String producto = detalle == null || detalle.isBlank() ? "Pago academico" : detalle.replace("|", " ").replaceAll("[\\r\\n]", " ").trim();
        if (producto.length() > 150) producto = producto.substring(0, 150);
        String p3 = "[{\"Serial\":1,\"Producto\":\"" + json(producto) + "\",\"LinkPago\":0,\"Cantidad\":1,\"Precio\":" + monto.toPlainString() + ",\"Descuento\":0,\"Total\":" + monto.toPlainString() + "}]";
        String cadena = String.join("|", servicio, email == null ? "" : email, telefono == null ? "" : telefono,
                pedidoId, monto.toPlainString(), "2", callback, retorno, p3, "11");
        return Base64.getEncoder().encodeToString(cadena.getBytes(StandardCharsets.UTF_8));
    }

    public String formulario(String email, String telefono, String pedidoId, BigDecimal monto, String detalle) {
        String datos = datos(email, telefono, pedidoId, monto, detalle);
        return "<form method=\"post\" action=\"" + url + "\">" +
                "<input type=\"hidden\" name=\"tcDatosCheckout\" value=\"" + html(datos) + "\">" +
                "<input type=\"hidden\" name=\"tcCommerceID\" value=\"" + html(comercio) + "\">" +
                "<button type=\"submit\">Pagar con Pago Fácil</button></form>";
    }

    private static String json(String v) { return v.replace("\\", "\\\\").replace("\"", "\\\""); }
    private static String html(String v) { return v.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;"); }
}
