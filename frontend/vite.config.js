import { copyFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// O CORS do backend (services/*/application.yml) só permite http://localhost:3000,
// então a porta é fixa: se 3000 estiver ocupada é melhor falhar do que subir em
// outra porta e ver toda requisição bloqueada.
//
// `base` fica no padrão '/' porque o site é servido na raiz de um domínio
// próprio (CNAME justdoit-app.duckdns.org), não numa subpasta do github.io.

// O GitHub Pages não conhece as rotas do React Router: recarregar /visao-geral
// faz ele procurar um arquivo com esse nome e devolver 404. Publicar o mesmo
// HTML como 404.html entrega o app, que então resolve a rota no cliente.
function fallbackSpa() {
  return {
    name: 'jdi-fallback-spa',
    closeBundle() {
      const dist = resolve(__dirname, 'dist');
      copyFileSync(resolve(dist, 'index.html'), resolve(dist, '404.html'));
    },
  };
}

export default defineConfig({
  plugins: [react(), fallbackSpa()],
  // Com a estrutura por feature, um import entre features sairia como
  // '../../../components/Ic'. O alias '@' aponta para src/ e mantém todo
  // import cruzado legível e imune a mudança de profundidade de pasta.
  resolve: {
    alias: { '@': resolve(__dirname, 'src') },
  },
  server: {
    // Sem `host`, o Vite resolve 'localhost' pela ordem verbatim do Node e acaba
    // escutando só em [::1]. O Chrome aqui resolve localhost para 127.0.0.1, então
    // a porta parecia no ar (o terminal mostrava a URL) mas o navegador levava
    // ECONNREFUSED. Fixar 127.0.0.1 garante que é exatamente onde o browser bate.
    // O Origin enviado continua sendo http://localhost:3000, que é o que o CORS
    // do backend exige — o bind não muda o cabeçalho.
    host: '127.0.0.1',
    port: 3000,
    strictPort: true,
  },
  // O Vitest lê este mesmo arquivo, então os testes já herdam o alias '@' e o
  // plugin React (JSX) sem duplicar configuração.
  test: {
    environment: 'jsdom',
    globals: true,
    // A suíte monta editores ricos e páginas completas; em máquinas/CI mais
    // lentos, interações reais do userEvent podem ultrapassar o padrão de 5 s.
    testTimeout: 15_000,
    setupFiles: './src/test/setup.js',
    include: ['src/**/*.test.{js,jsx}'],
    // Todo.perf.test mede tempo de parede. Em paralelo ele disputa CPU com
    // páginas/editor ricos e reprova por carga do runner, não por regressão do
    // filtro. Arquivos serializados mantêm o SLA de 500 ms sem relaxar o gate.
    fileParallelism: false,
  },
});
