import { Component } from '@angular/core';

interface CampoInput {
  el: HTMLInputElement | null;
  erro: HTMLElement | null;
  mensagem: string;
  validar?: (valor: string) => boolean;
}

@Component({
  selector: 'app-cadastro',
  imports: [],
  templateUrl: './cadastro.html',
  styleUrl: './cadastro.scss',
})
export class Cadastro {
  private form!: HTMLFormElement;
  private btnGerar!: HTMLElement;
  private inputNome!: HTMLInputElement;
  private outputEmail!: HTMLInputElement;
  private outputSenha!: HTMLInputElement;
  private alertSucesso!: HTMLElement;
  private alertErro!: HTMLElement;

  private init(): void {
    this.btnGerar!.addEventListener('click', (e: Event) => {
      e.preventDefault();
      this.gerarAcesso();
    });
  }
  // ── Regras de validação do formulário ──────────────────────────────────────
  // Cada campo tem: o elemento input, o div de erro, a mensagem e uma função validar opcional
  private get camposObrigatorios(): CampoInput[] {
    return [
      {
        el: this.getInput('nome'),
        erro: this.getEl('erroNome'),
        mensagem: 'Nome é obrigatório.',
      },
      {
        el: this.getInput('cpf'),
        erro: this.getEl('erroCpf'),
        mensagem: 'CPF inválido.',
        validar: (v) => /^\d{3}\.\d{3}\.\d{3}-\d{2}$/.test(v),
      },
      {
        el: this.getInput('emailResponsavel'),
        erro: this.getEl('erroEmailResponsavel'),
        mensagem: 'E-mail do responsável inválido.',
        validar: (v) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v),
      },
      {
        el: this.getInput('endereco'),
        erro: this.getEl('erroEndereco'),
        mensagem: 'Endereço é obrigatório.',
      },
      {
        el: this.getInput('dataNascimento'),
        erro: this.getEl('erroData'),
        mensagem: 'Data de nascimento é obrigatória.',
      },
    ];
  }
  ngAfterViewInit(): void {
    this.form = this.getEl('formCadastro') as HTMLFormElement;
    this.inputNome = this.getInput('nome');
    this.outputEmail = this.getInput('emailGerado');
    this.outputSenha = this.getInput('senhaGerada');
    this.btnGerar = this.getEl('btnGerarAcesso') as HTMLElement;
    this.alertSucesso = this.getEl('alertSucesso') as HTMLElement;
    this.alertErro = this.getEl('alertErro') as HTMLElement;

    this.ouvirBtnGerar();
    this.ouvirSubmit();

  }
  private ouvirBtnGerar(): void {
    this.btnGerar?.addEventListener('click', () => this.gerarAcesso());
  }

  private ouvirSubmit(): void {
    this.form?.addEventListener('submit', (e: Event) => {
      e.preventDefault();
      this.finalizar();
    });
  }


  private normalizarTexto(texto: string): string {
    return texto
      .toLowerCase()
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '');
  }

  private gerarLogin(nomeCompleto: string): string {
    const partes: string[] = nomeCompleto.split(' ').filter(p => p.length > 0);

    const primeiroNome: string = this.normalizarTexto(partes[0]);

    // Se houver sobrenome, usa o último para diferenciar alunos com o mesmo primeiro nome
    if (partes.length > 1) {
      const ultimoSobrenome: string = this.normalizarTexto(partes[partes.length - 1]);
      return `${primeiroNome}.${ultimoSobrenome}@nexo.com.br`;
    }

    return `${primeiroNome}@nexo.com.br`;
  }

  private gerarSenha(): string {
    const letrasMin: string = 'abcdefghijklmnopqrstuvwxyz';
    const letrasMai: string = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ';
    const numeros: string = '0123456789';
    const especiais: string = '!@#$';

    const todosCaracteres: string = letrasMin + letrasMai + numeros + especiais;

    // Garante ao menos 1 de cada categoria obrigatória
    const senhaArray: string[] = [
      letrasMin[Math.floor(Math.random() * letrasMin.length)],
      letrasMai[Math.floor(Math.random() * letrasMai.length)],
      numeros[Math.floor(Math.random() * numeros.length)],
      numeros[Math.floor(Math.random() * numeros.length)],
    ];

    // Completa até 8 caracteres com caracteres aleatórios
    for (let i = senhaArray.length; i < 8; i++) {
      senhaArray.push(todosCaracteres[Math.floor(Math.random() * todosCaracteres.length)]);
    }

    // Embaralha para não ter padrão previsível
    return senhaArray.sort(() => Math.random() - 0.5).join('');
  }

  private gerarAcesso(): void {
    const nomeCompleto: string = (this.inputNome?.value ?? '').trim();

    if (!nomeCompleto) {
      alert('Digite o nome do aluno antes de gerar o acesso.');
      return;
    }

    const login: string = this.gerarLogin(nomeCompleto);
    const senha: string = this.gerarSenha();

    if (this.outputEmail) this.outputEmail.value = login;
    if (this.outputSenha) this.outputSenha.value = senha;

    this.exibirAlerta(this.alertSucesso, `✅ Acesso gerado! Login: ${login} | Senha: ${senha}`);

    console.log('Acesso gerado:', { email: login, senha });
  }
  private exibirAlerta(el: HTMLElement, mensagem: string): void {
    if (!el) return;
    el.textContent = mensagem;
    el.style.display = 'block';
    setTimeout(() => (el.style.display = 'none'), 6000);
  }
  private ocultarAlertas(): void {
    [this.alertSucesso, this.alertErro].forEach((el) => {
      if (el) el.style.display = 'none';
    });
  }
  private finalizar(): void {
    this.ocultarAlertas();
    this.limparErros();

    const valido = this.validarFormulario();

    if (!valido) {
      this.exibirAlerta(this.alertErro, 'Corrija os campos destacados antes de finalizar.');
      return;
    }

    if (!this.outputEmail.value || !this.outputSenha.value) {
      this.exibirAlerta(this.alertErro, 'Gere o acesso do aluno antes de finalizar o cadastro.');
      return;
    }

    console.log('Dados prontos para envio:', this.coletarDados());
    this.exibirAlerta(this.alertSucesso, '✅ Cadastro finalizado com sucesso!');
  }
  private validarFormulario(): boolean {
    let tudo_valido = true;

    for (const campo of this.camposObrigatorios) {
      const valor = campo.el?.value.trim() ?? '';
      const vazio = !valor;
      const invalido = campo.validar ? !campo.validar(valor) : false;

      if (vazio || invalido) {
        this.marcarInvalido(campo.el, campo.erro, campo.mensagem);
        tudo_valido = false;
      }
    }

    return tudo_valido;
  }

  private marcarInvalido(
    input: HTMLInputElement | null,
    erroEl: HTMLElement | null,
    mensagem: string
  ): void {
    input?.classList.add('is-invalid');
    if (erroEl) erroEl.textContent = mensagem;
  }

  private limparErros(): void {
    this.camposObrigatorios.forEach(({ el, erro }) => {
      el?.classList.remove('is-invalid');
      if (erro) erro.textContent = '';
    });
  }
  // ── Coleta de dados para envio futuro ─────────────────────────────────────
  private coletarDados(): Record<string, string> {
    const ids = [
      'nome', 'cpf', 'sexo', 'telefone', 'dataNascimento',
      'emailResponsavel', 'cpfResponsavel', 'telefoneResponsavel',
      'endereco', 'complemento', 'emailGerado', 'senhaGerada',
    ];

    return Object.fromEntries(
      ids.map((id) => [id, (this.getInput(id))?.value ?? ''])
    );
  }

  //── Helpers ────────────────────────────────────────────────────────────────
  private getEl(id: string): HTMLElement {
    return document.getElementById(id) as HTMLElement;
  }

  private getInput(id: string): HTMLInputElement {
    return document.getElementById(id) as HTMLInputElement;
  }

}