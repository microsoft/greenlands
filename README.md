# greenlands

This repository contains the code for `greenlands`, a platform that enables
gameplay between _AI agents_ and human players in Minecraft, as well as the
collection of game metrics.

The code is essentially the same as what was used to gather human evaluation
data for the [IGLU
2022](https://www.aicrowd.com/challenges/neurips-2022-iglu-challenge)
competition. It is intended to serve as a reference for anyone interested in
developing a similar system or for those curious about the implementation
details.


**NOTE:** The code is provided "as-is" with the hope that it will be useful for
the open-source community. It's not under active development, but pull requests
are welcome, and we'll address issues when possible.

The documentation can be found
[here](https://github.com/microsoft/greenlands/blob/main/Docs/Home.md), and
(while not exhaustive) should give you an idea of the architecture and
functionality of the different components.

For an example of a human interacting with an AI agent, where the agent acts on
the human's instructions, you can view the full video
[here](https://www.youtube.com/watch?v=PWrvLhQDybw).

[3452346.webm](https://user-images.githubusercontent.com/3422347/232140380-4605b2f8-2533-45d4-b389-d49f3c0ced1e.webm)


## Contributing

This project welcomes contributions and suggestions.  Most contributions require you to agree to a
Contributor License Agreement (CLA) declaring that you have the right to, and actually do, grant us
the rights to use your contribution. For details, visit https://cla.opensource.microsoft.com.

When you submit a pull request, a CLA bot will automatically determine whether you need to provide
a CLA and decorate the PR appropriately (e.g., status check, comment). Simply follow the instructions
provided by the bot. You will only need to do this once across all repos using our CLA.

This project has adopted the [Microsoft Open Source Code of Conduct](https://opensource.microsoft.com/codeofconduct/).
For more information see the [Code of Conduct FAQ](https://opensource.microsoft.com/codeofconduct/faq/) or
contact [opencode@microsoft.com](mailto:opencode@microsoft.com) with any additional questions or comments.


## Trademarks

This project may contain trademarks or logos for projects, products, or services. Authorized use of Microsoft 
trademarks or logos is subject to and must follow 
[Microsoft's Trademark & Brand Guidelines](https://www.microsoft.com/en-us/legal/intellectualproperty/trademarks/usage/general).
Use of Microsoft trademarks or logos in modified versions of this project must not cause confusion or imply Microsoft sponsorship.
Any use of third-party trademarks or logos are subject to those third-party's policies.

## References

The greenwolds was inspired as a playground for human in the loop evaluation for the competition [IGLU:Interactive Grounded Language Understanding in a Collaborative Environment](https://www.aicrowd.com/challenges/neurips-2022-iglu-challenge), which is described in the following papers:

```
@inproceedings{kiseleva2022interactive,
  title={Interactive grounded language understanding in a collaborative environment: Iglu 2021},
  author={Kiseleva, Julia and Li, Ziming and Aliannejadi, Mohammad and Mohanty, Shrestha and ter Hoeve, Maartje and Burtsev, Mikhail and Skrynnik, Alexey and Zholus, Artem and Panov, Aleksandr and Srinet, Kavya and others},
  booktitle={NeurIPS 2021 Competitions and Demonstrations Track},
  pages={146--161},
  year={2022},
  organization={PMLR}
}
```

```
@article{kiseleva2022iglu,
  title={Iglu 2022: Interactive grounded language understanding in a collaborative environment at neurips 2022},
  author={Kiseleva, Julia and Skrynnik, Alexey and Zholus, Artem and Mohanty, Shrestha and Arabzadeh, Negar and C{\^o}t{\'e}, Marc-Alexandre and Aliannejadi, Mohammad and Teruel, Milagro and Li, Ziming and Burtsev, Mikhail and others},
  journal={arXiv preprint arXiv:2205.13771},
  year={2022}
}
```

```
@article{zholus2022iglu,
  title={IGLU Gridworld: Simple and Fast Environment for Embodied Dialog Agents},
  author={Zholus, Artem and Skrynnik, Alexey and Mohanty, Shrestha and Volovikova, Zoya and Kiseleva, Julia and Szlam, Artur and Cot{\'e}, Marc-Alexandre and Panov, Aleksandr I},
  journal={arXiv preprint arXiv:2206.00142},
  year={2022}
}
```

