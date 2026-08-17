$ErrorActionPreference = "Stop"

$Model = "qwen2.5-coder:3b"
$OllamaUrl = "http://localhost:11434/api/chat"

Write-Host ""
Write-Host "==========================================" 
Write-Host " Ollama PowerShell Coding Agent"
Write-Host " Model: $Model"
Write-Host " Repo:  $(Get-Location)"
Write-Host "=========================================="
Write-Host ""
Write-Host "Type your coding task."
Write-Host "Type /exit to quit."
Write-Host ""

function Invoke-Ollama {
    param(
        [array]$Messages
    )

    $body = @{
        model    = $Model
        messages = $Messages
        stream   = $false
    } | ConvertTo-Json -Depth 20

    $response = Invoke-RestMethod `
        -Uri $OllamaUrl `
        -Method Post `
        -ContentType "application/json" `
        -Body $body

    return $response.message.content
}

$systemPrompt = @"
You are a coding agent operating inside a Windows PowerShell repository.

Current repository:
$(Get-Location)

You must NEVER pretend that you executed an action.

You cannot directly execute tools.

When you need an action, output EXACTLY ONE JSON object and nothing else.

Supported actions:

READ:
{"action":"read","path":"relative/path"}

SEARCH:
{"action":"search","pattern":"text","path":"relative/path"}

GIT_SHOW:
{"action":"git_show","stage":"2","path":"relative/path"}

EDIT:
{"action":"edit","path":"relative/path","content":"complete new file content"}

RUN:
{"action":"run","command":"command to execute"}

DONE:
{"action":"done","message":"short final result"}

Rules:
- Use paths relative to the repository.
- Never use absolute paths.
- Never execute git reset.
- Never execute git clean.
- Never execute git restore.
- Never execute git checkout.
- Never execute git commit.
- Never execute git push.
- Do not modify files unless the user explicitly asks for coding changes.
- Prefer reading before editing.
- After editing, run the smallest appropriate validation.
- Never claim an action succeeded unless the tool result confirms it.
- When you need multiple actions, request them one at a time.
"@

$messages = @(
    @{
        role    = "system"
        content = $systemPrompt
    }
)

while ($true) {

    Write-Host ""
    $task = Read-Host "TASK"

    if ([string]::IsNullOrWhiteSpace($task)) {
        continue
    }

    if ($task -eq "/exit") {
        Write-Host "Exiting agent."
        break
    }

    $messages += @{
        role    = "user"
        content = $task
    }

    while ($true) {

        Write-Host ""
        Write-Host "[QWEN] Thinking..." -ForegroundColor Cyan

        $content = Invoke-Ollama -Messages $messages

        Write-Host ""
        Write-Host "[MODEL]" -ForegroundColor Yellow
        Write-Host $content

        # Extract JSON object if model wrapped it in markdown.
        $jsonMatch = [regex]::Match(
            $content,
            '\{[\s\S]*\}'
        )

        if (-not $jsonMatch.Success) {
            $messages += @{
                role    = "assistant"
                content = $content
            }

            Write-Host ""
            Write-Host "[INFO] Model returned normal text." -ForegroundColor DarkGray
            break
        }

        try {
            $action = $jsonMatch.Value | ConvertFrom-Json
        }
        catch {
            Write-Host "[ERROR] Invalid JSON from model." -ForegroundColor Red
            break
        }

        $messages += @{
            role    = "assistant"
            content = $content
        }

        switch ($action.action) {

            "read" {

                $path = $action.path

                Write-Host ""
                Write-Host "[TOOL] READ: $path" -ForegroundColor Green

                if ($path -match '(^|[\\/])\.\.([\\/]|$)' -or [IO.Path]::IsPathRooted($path)) {
                    $result = "ERROR: Path is outside the repository."
                }
                else {
                    $fullPath = Join-Path (Get-Location) $path

                    if (Test-Path -LiteralPath $fullPath -PathType Leaf) {
                        $result = Get-Content -LiteralPath $fullPath -Raw
                    }
                    else {
                        $result = "ERROR: File not found: $path"
                    }
                }
            }

            "search" {

                $pattern = $action.pattern
                $path = $action.path

                Write-Host ""
                Write-Host "[TOOL] SEARCH: '$pattern' in $path" -ForegroundColor Green

                if ($path -match '(^|[\\/])\.\.([\\/]|$)' -or [IO.Path]::IsPathRooted($path)) {
                    $result = "ERROR: Invalid path."
                }
                else {
                    $fullPath = Join-Path (Get-Location) $path

                    if (Test-Path $fullPath) {
                        $result = Get-ChildItem -LiteralPath $fullPath -Recurse -File |
                            Select-String -Pattern $pattern |
                            Out-String
                    }
                    else {
                        $result = "ERROR: Path not found."
                    }
                }
            }

            "git_show" {

                $stage = $action.stage
                $path = $action.path

                Write-Host ""
                Write-Host "[TOOL] git show :$stage`:$path" -ForegroundColor Green

                if ($path -match '(^|[\\/])\.\.([\\/]|$)' -or [IO.Path]::IsPathRooted($path)) {
                    $result = "ERROR: Invalid path."
                }
                else {
                    $result = & git show ":$stage`:$path" 2>&1 | Out-String
                }
            }

            "edit" {

                $path = $action.path

                Write-Host ""
                Write-Host "[TOOL] EDIT: $path" -ForegroundColor Magenta

                if ($path -match '(^|[\\/])\.\.([\\/]|$)' -or [IO.Path]::IsPathRooted($path)) {
                    $result = "ERROR: Invalid path."
                }
                else {
                    $fullPath = Join-Path (Get-Location) $path

                    # Explicit confirmation before modification.
                    Write-Host ""
                    Write-Host "The agent wants to MODIFY:" -ForegroundColor Yellow
                    Write-Host $path -ForegroundColor Yellow

                    $confirm = Read-Host "Allow this edit? (y/n)"

                    if ($confirm -eq "y") {
                        Set-Content `
                            -LiteralPath $fullPath `
                            -Value $action.content `
                            -Encoding UTF8

                        $result = "EDIT SUCCESS: $path"
                    }
                    else {
                        $result = "EDIT REJECTED BY USER."
                    }
                }
            }

            "run" {

                $command = $action.command

                Write-Host ""
                Write-Host "[TOOL] RUN:" -ForegroundColor Magenta
                Write-Host $command -ForegroundColor Yellow

                # Block dangerous Git operations.
                if ($command -match 'git\s+(reset|clean|restore|checkout|commit|push)\b') {
                    $result = "COMMAND BLOCKED: destructive Git operation is not allowed."
                }
                else {

                    $confirm = Read-Host "Allow command? (y/n)"

                    if ($confirm -eq "y") {
                        $result = Invoke-Expression $command 2>&1 | Out-String
                    }
                    else {
                        $result = "COMMAND REJECTED BY USER."
                    }
                }
            }

            "done" {

                Write-Host ""
                Write-Host "[DONE]" -ForegroundColor Green
                Write-Host $action.message

                break
            }

            default {

                $result = "ERROR: Unknown action '$($action.action)'."
            }
        }

        if ($action.action -eq "done") {
            break
        }

        $messages += @{
            role    = "user"
            content = "TOOL RESULT:`n$result"
        }
    }
}