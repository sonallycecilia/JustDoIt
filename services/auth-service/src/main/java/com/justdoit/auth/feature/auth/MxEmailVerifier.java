package com.justdoit.auth.feature.auth;

import org.springframework.stereotype.Component;

import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.Hashtable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Verifica a entregabilidade do e-mail consultando os registros DNS do domínio.
 *
 * Considera entregável se o domínio possui registro MX (ou, como fallback, um
 * registro A — domínios sem MX ainda podem receber e-mail no próprio A).
 * Não confirma a caixa específica; sem enviar um código de confirmação não há
 * como ter 100% de certeza. Cobre, porém, a maioria dos erros de digitação de
 * domínio (ex.: "gmial.com").
 */
@Component
public class MxEmailVerifier implements EmailVerifier {

    // Limite rígido para o lookup DNS — garante que a verificação nunca segure a
    // conexão da requisição mesmo que o provedor JNDI ignore os timeouts internos.
    private static final long LOOKUP_TIMEOUT_MS = 4000;

    private static final ExecutorService DNS_POOL = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "mx-dns-lookup");
        t.setDaemon(true);
        return t;
    });

    @Override
    public boolean isDeliverable(String email) {
        if (email == null) return false;
        int at = email.lastIndexOf('@');
        if (at < 0 || at == email.length() - 1) return false;
        String domain = email.substring(at + 1).trim();
        if (domain.isEmpty()) return false;

        Future<Boolean> future = DNS_POOL.submit(() -> hasMailRecords(domain));
        try {
            return future.get(LOOKUP_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return true; // lookup lento: não bloqueia o cadastro
        } catch (Exception e) {
            return true; // qualquer falha inesperada: não bloqueia
        }
    }

    private boolean hasMailRecords(String domain) {
        Hashtable<String, String> env = new Hashtable<>();
        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        // Resolvedores públicos explícitos (IPv4) + o resolvedor do sistema como
        // último recurso ("dns:"). Sem isso, o lookup depende do DNS do sistema —
        // que pode ser IPv6-only e, com respostas MX grandes (gmail/hotmail têm
        // vários registros → UDP truncado → fallback TCP), falha de forma
        // intermitente e lança NameNotFoundException para domínios que existem.
        env.put("java.naming.provider.url", "dns://8.8.8.8 dns://1.1.1.1 dns:");
        // Timeouts curtos: a verificação roda no caminho do cadastro.
        env.put("com.sun.jndi.dns.timeout.initial", "2000");
        env.put("com.sun.jndi.dns.timeout.retries", "2");

        // Nome absoluto (FQDN com ponto final): impede o resolvedor de anexar um
        // sufixo de busca local ao domínio (ex.: "gmail.com.lan"), o que também
        // produziria NameNotFoundException espúrio.
        String fqdn = domain.endsWith(".") ? domain : domain + ".";

        DirContext ctx = null;
        try {
            ctx = new InitialDirContext(env);
            Attributes attrs = ctx.getAttributes(fqdn, new String[]{"MX", "A"});
            Attribute mx = attrs.get("MX");
            if (mx != null && mx.size() > 0) return true;
            Attribute a = attrs.get("A");
            return a != null && a.size() > 0;
        } catch (NameNotFoundException e) {
            // Domínio não existe: definitivamente não entregável.
            return false;
        } catch (NamingException e) {
            // Falha transitória de DNS (timeout/rede): não bloqueia o cadastro.
            return true;
        } finally {
            if (ctx != null) {
                try { ctx.close(); } catch (NamingException ignored) { }
            }
        }
    }
}
