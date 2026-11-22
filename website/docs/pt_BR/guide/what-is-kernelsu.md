# O que é MuSu Next?

O MuSu Next é uma solução root para dispositivos Android GKI, funciona no modo kernel e concede privilégios root para apps do espaço do usuário diretamente no espaço do kernel.

## Características

A principal característica do MuSu Next é que ele é **baseado em kernel**. O MuSu Next funciona no modo kernel, portanto pode fornecer uma interface de kernel que nunca tivemos antes. Por exemplo, é possível adicionar pontos de interrupção de hardware a qualquer processo no modo kernel, acessar a memória física de qualquer processo de forma invisível, interceptar qualquer chamada de sistema (syscall) no espaço do kernel, entre outras funcionalidades.

Além disso, o MuSu Next fornece um [sistema metamodule](metamodule.md), que é uma arquitetura plugável para gerenciamento de módulos. Diferente das soluções root tradicionais que integram a lógica de montagem em seu núcleo, o MuSu Next delega isso aos metamodules. Isso permite que você instale metamodules como [meta-overlayfs](https://github.com/bilibiliHaoziyao/MuSuNext/tree/main/userspace/meta-overlayfs) para fornecer modificações systemless na partição `/system` e outras partições.

## Como usar o MuSu Next?

Por favor, consulte: [Instalação](installation)

## Como compilar o MuSu Next?

Por favor, consulte: [Como compilar](how-to-build)

## Discussão

- Telegram: [@MuSu Next](https://b23.tv/6UtmCB0)
