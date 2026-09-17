export const validarSenha = (senha = '') => {
  const rules = [
    { 
      id: 'length', 
      label: 'Mínimo de 8 caracteres', 
      isValid: senha.length >= 8 
    },
    { 
      id: 'uppercase', 
      label: 'Pelo menos uma letra maiúscula', 
      isValid: /[A-Z]/.test(senha) 
    },
    { 
      id: 'lowercase', 
      label: 'Pelo menos uma letra minúscula', 
      isValid: /[a-z]/.test(senha) 
    },
    { 
      id: 'number', 
      label: 'Pelo menos um número', 
      isValid: /[0-9]/.test(senha) 
    },
  ];

  // A senha só é válida se TODAS as regras passarem
  const isValid = rules.every(rule => rule.isValid);

  return { rules, isValid };
};