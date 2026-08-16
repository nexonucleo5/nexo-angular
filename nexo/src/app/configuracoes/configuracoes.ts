import { Component, inject } from '@angular/core';
import { AuthService } from '../services/auth.service';
import { ConfiguracaoAluno } from '../configuracao-aluno/configuracao-aluno';
import { ConfiguracaoProfessor } from '../configuracao-professor/configuracao-professor';
import { ConfiguracaoDiretor } from '../configuracao-diretor/configuracao-diretor';
import { ConfiguracaoSecretaria } from '../configuracao-secretaria/configuracao-secretaria';

/**
 * Wrapper da rota /configuracoes: renderiza a tela de configuração do perfil
 * logado (Task 4) — mesmo padrão do matriculas-wrapper.
 */
@Component({
  selector: 'app-configuracoes',
  imports: [ConfiguracaoAluno, ConfiguracaoProfessor, ConfiguracaoDiretor, ConfiguracaoSecretaria],
  templateUrl: './configuracoes.html',
})
export class Configuracoes {
  public authService = inject(AuthService);
}
