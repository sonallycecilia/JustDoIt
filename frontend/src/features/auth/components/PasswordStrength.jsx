import React from 'react';
import { validarSenha } from '@/lib/senha';
import './PasswordStrength.css';

export const PasswordStrength = ({ password = '' }) => {
  // Consome as regras do utilitário
  const { rules } = validarSenha(password);

  return (
    <div 
      className="password-strength-wrapper"
      aria-live="polite" 
      aria-atomic="true"
    >
      <span className="sr-only">Status dos requisitos da senha:</span>
      <ul className="password-rules-list">
        {rules.map((rule) => (
          <li 
            key={rule.id} 
            className="password-rule-item"
            style={{
              color: rule.isValid ? 'var(--color-success)' : 'var(--color-password-rule-invalid)',
            }}
          >
            <span aria-hidden="true" className="rule-icon">
              {rule.isValid ? '✓' : 'X'}
            </span>
            <span>{rule.label}</span>
          </li>
        ))}
      </ul>
    </div>
  );
};
