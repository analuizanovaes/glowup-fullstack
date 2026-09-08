# 💄 Projeto GlowUp

Plataforma full-stack de e-commerce desenvolvida para a revenda local de cosméticos. Este repositório centraliza o código do Back-end e do Front-end do sistema.

**Arquitetura do Sistema**
* **Back-end:** Desenvolvido em Java com o framework Spring Boot.
* **Front-end:** Construído com HTML, CSS e JavaScript puros (Vanilla), garantindo leveza e integração direta com a API, sem a necessidade de frameworks pesados.
* **Banco de Dados:** Utilização do H2 Database (em memória) para desenvolvimento e testes no laboratório, facilitando a execução sem configurações complexas de infraestrutura. O mapeamento relacional final será migrado para PostgreSQL.
* **Documentação de API:** Contratos disponíveis e testáveis via Swagger (OpenAPI).

**Regras de Versionamento e Git Flow**
Para manter a estabilidade e a segurança do código, é **terminantemente proibido fazer commit direto na branch `main`**. 

Toda alteração desenvolvida pela equipe (Ana Luiza, Yasmin, Sthefanny, Emerson e Marcelo) deve seguir obrigatoriamente o seguinte fluxo:

1. **Criação de Branch:** Toda nova ramificação deve ser criada a partir da `main` seguindo o padrão (tudo em minúsculo, sem acentos e separado por hífen):
   * `feature/nome-da-tarefa` -> Para desenvolver novas telas, rotas ou regras de negócio (Ex: `feature/tela-de-login`).
   * `fix/nome-do-erro` -> Para corrigir bugs em algo que já estava pronto (Ex: `fix/erro-carrinho`).
   * `chore/nome-da-configuracao` -> Para tarefas de infraestrutura, banco, dependências ou setup (Ex: `chore/setup-banco-h2`).
   * `docs/nome-do-documento` -> Exclusivo para escrita de documentação (Ex: `docs/atualiza-requisitos`).

2. **Pull Requests (PR):** Ao finalizar o código na sua branch, abra um Pull Request para a `main`.
3. **Revisão:** O código só será integrado à `main` após aprovação técnica, garantindo a aplicação de Clean Code, métodos com responsabilidade única e variáveis com nomenclatura clara e em português.
