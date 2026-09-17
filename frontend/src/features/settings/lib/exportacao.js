// Regras próprias da exportação de dados: os formatos oferecidos e o
// "salvar como" do navegador. Sem React aqui, para poder testar direto.

export const FORMATOS = [
  { valor: 'csv', rotulo: 'CSV', descricao: 'Para planilhas — Excel, Google Sheets, Numbers' },
  { valor: 'json', rotulo: 'JSON', descricao: 'Backup completo, com a estrutura original dos dados' },
];

// Data local (não UTC): o arquivo leva a data que o usuário vê no relógio dele.
function hojeISO(agora = new Date()) {
  const mes = String(agora.getMonth() + 1).padStart(2, '0');
  const dia = String(agora.getDate()).padStart(2, '0');
  return `${agora.getFullYear()}-${mes}-${dia}`;
}

// Só entra em cena quando o Content-Disposition não chega ao JS (proxy que não
// repassa o cabeçalho). O padrão é o mesmo nome que o backend geraria.
export function nomePadrao(formato, agora = new Date()) {
  return `export_tarefas_${hojeISO(agora)}.${formato}`;
}

export function salvarArquivo(blob, nomeArquivo) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = nomeArquivo;
  link.style.display = 'none';
  document.body.appendChild(link);
  link.click();

  // O clique apenas agenda o download. Remover o link ou revogar a URL logo no
  // tick seguinte ainda pode acontecer antes de o navegador abrir o Blob — em
  // especial no Safari e em arquivos maiores — e o arquivo nunca é baixado.
  // Mantemos ambos vivos por tempo suficiente e só então liberamos os recursos.
  setTimeout(() => {
    link.remove();
    URL.revokeObjectURL(url);
  }, 60_000);
}
