"""
Sandboxed agent adapters that activate a DNS sinkhole after agent setup.

These adapters prevent agents from downloading original source code from
GitHub/GitLab during benchmark runs. The sinkhole is written to /etc/hosts
as root before the agent gets any tool-use turns — the agent (running as
non-root `builder`) cannot undo it.

Usage:
    uvx harbor run ... --agent-import-path agents.sandboxed:OpenCodeSandboxed
    uvx harbor run ... --agent-import-path agents.sandboxed:ClaudeCodeSandboxed
"""

from harbor.agents.installed.opencode import OpenCode
from harbor.agents.installed.claude_code import ClaudeCode
from harbor.environments.base import BaseEnvironment

# Domains to sinkhole — covers all major source hosting platforms.
SINKHOLE_DOMAINS = [
    "github.com",
    "raw.githubusercontent.com",
    "api.github.com",
    "objects.githubusercontent.com",
    "codeload.github.com",
    "gist.github.com",
    "gitlab.com",
    "bitbucket.org",
    "codeberg.org",
    "sr.ht",
    "huggingface.co",
    "hf.co",
    "sourceforge.net",
]


async def _activate_sinkhole(agent, environment: BaseEnvironment) -> None:
    """Append DNS sinkhole entries to /etc/hosts inside the container."""
    entries = "\\n".join(f"0.0.0.0 {d}" for d in SINKHOLE_DOMAINS)
    await agent.exec_as_root(
        environment,
        command=f'printf "{entries}\\n" >> /etc/hosts',
    )


class OpenCodeSandboxed(OpenCode):
    """OpenCode adapter with DNS sinkhole and pre-installed dependencies.

    Expects NVM, Node 22, and opencode-ai to already be installed in the
    Docker image (done at build time). Skips the upstream install step
    that curls from raw.githubusercontent.com, then activates the DNS
    sinkhole before the agent runs.
    """

    @staticmethod
    def name() -> str:
        return "opencode-sandboxed"

    async def install(self, environment: BaseEnvironment) -> None:
        # Install curl (same as upstream, needed for other things)
        await self.exec_as_root(
            environment,
            command="apt-get update && apt-get install -y curl",
            env={"DEBIAN_FRONTEND": "noninteractive"},
        )

        # Verify pre-installed opencode is available (skip NVM download)
        await self.exec_as_agent(
            environment,
            command=(
                'export NVM_DIR="$HOME/.nvm" && '
                '. "$NVM_DIR/nvm.sh" && '
                "opencode --version"
            ),
        )

        # Activate DNS sinkhole AFTER setup, BEFORE agent runs
        await _activate_sinkhole(self, environment)


class ClaudeCodeSandboxed(ClaudeCode):
    """Claude Code adapter with DNS sinkhole activated after install.

    Runs the normal Claude Code install, then appends DNS sinkhole entries
    to /etc/hosts before the agent runs.
    """

    @staticmethod
    def name() -> str:
        return "claude-code-sandboxed"

    async def install(self, environment: BaseEnvironment) -> None:
        # Normal Claude Code install (downloads from claude.ai, not GitHub)
        await super().install(environment)

        # Activate DNS sinkhole AFTER setup, BEFORE agent runs
        await _activate_sinkhole(self, environment)
