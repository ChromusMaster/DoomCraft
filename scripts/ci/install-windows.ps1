$ErrorActionPreference = "Stop"

Write-Host "Visual Studio installation:"
Get-ChildItem Env: | Where-Object { $_.Name -match "^(VisualStudio|VSCMD|VCPKG)" } | Sort-Object Name

cmake --version
python --version

if (-not $env:VCPKG_INSTALLATION_ROOT) {
    throw "VCPKG_INSTALLATION_ROOT is not defined on the Windows runner."
}

$toolchain = Join-Path $env:VCPKG_INSTALLATION_ROOT "scripts\buildsystems\vcpkg.cmake"
if (-not (Test-Path $toolchain)) {
    throw "vcpkg toolchain not found: $toolchain"
}
Write-Host "vcpkg toolchain: $toolchain"
