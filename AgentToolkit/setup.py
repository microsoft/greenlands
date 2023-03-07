
from setuptools import setup

REQUIRES = [
    "python-dotenv>=0.20.0",
    "azure-eventhub>=5.10.0",
    "requests>=2.27.1",
    "gym==0.24.0",
    "python-dateutil>=2.8.2",
    "mypy>=0.961",
    "types-python-dateutil>=2.8.19",
    "types-requests>=2.28.3",
]

setup(
    name='agent_toolkit',
    version='0.1',
    description='Agent toolkit demo code to connect RL model with Minecraft server.',
    author='Microsoft Deep Learning Engineering Team',
    author_email='',
    packages=['agent_toolkit'],
    install_requires=REQUIRES,
)