param(
    [string]$UpstreamUrl = "https://github.com/SukiSU-Ultra/SukiSU-Ultra.git",
    [string]$UpstreamBranch = "main",
    [string]$TrackingBranch = "codex/upstream-main",
    [string]$CustomBranch = "codex/s789-manager-custom",
    [switch]$Push
)

$ErrorActionPreference = "Stop"

function Run-Git {
    param([string[]]$Args)
    & git @Args
    if ($LASTEXITCODE -ne 0) {
        throw "git $($Args -join ' ') failed"
    }
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $repoRoot

$remoteNames = (& git remote).Trim().Split([Environment]::NewLine, [System.StringSplitOptions]::RemoveEmptyEntries)
if ($remoteNames -notcontains "upstream") {
    Run-Git @("remote", "add", "upstream", $UpstreamUrl)
}

Run-Git @("fetch", "upstream", $UpstreamBranch)
Run-Git @("fetch", "origin")

$trackingExists = (& git branch --list $TrackingBranch).Trim()
if ([string]::IsNullOrWhiteSpace($trackingExists)) {
    Run-Git @("checkout", "-b", $TrackingBranch, "upstream/$UpstreamBranch")
} else {
    Run-Git @("checkout", $TrackingBranch)
    Run-Git @("reset", "--hard", "upstream/$UpstreamBranch")
}

Run-Git @("checkout", $CustomBranch)
Run-Git @("merge", "--no-edit", $TrackingBranch)

if ($Push) {
    Run-Git @("push", "origin", $TrackingBranch)
    Run-Git @("push", "origin", $CustomBranch)
}
