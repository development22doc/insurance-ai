# Local config-server repo

This folder is meant to become a standalone git repo that config-service points at for local
Windows development. See WINDOWS_LOCAL_SETUP.md Step 3 for the full instructions.

Quick version (PowerShell):
    mkdir C:\claimassist-config
    # copy these 4 .yml files into it
    cd C:\claimassist-config
    git init
    git branch -M main
    git add .
    git commit -m "local config"

Then set, before starting config-service:
    $env:CONFIG_REPO_URI = "file:///C:/claimassist-config"
    $env:GIT_USERNAME = "local"
    $env:GIT_PASSWORD = "local"

IMPORTANT: jwt.secret-key and internal.api.secret must be identical across all 4 files.
