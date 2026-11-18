param([string]$WebDirectory = (Join-Path $PSScriptRoot '..'))
$ErrorActionPreference = 'Stop'
$web = (Resolve-Path $WebDirectory).Path
$html = [IO.File]::ReadAllText("$web/index.html")
$html = $html.Replace('<link rel="stylesheet" href="style.css">', '<style>'+[IO.File]::ReadAllText("$web/style.css")+'</style>')
$html = $html.Replace('<link rel="manifest" href="manifest.webmanifest">','')
$html = $html.Replace('href="icon-192.png"', ('href="data:image/png;base64,'+[Convert]::ToBase64String([IO.File]::ReadAllBytes("$web/icon-192.png"))+'"'))
foreach($script in 'launch.js','demo.js','app.js'){
    $html=$html.Replace(('<script src="'+$script+'"></script>'),('<script>'+[IO.File]::ReadAllText("$web/$script")+'</script>'))
}
[IO.File]::WriteAllText("$PSScriptRoot/studio.html",$html)
Push-Location $PSScriptRoot
try {
    & "$env:WINDIR/Microsoft.NET/Framework64/v4.0.30319/csc.exe" /nologo /target:winexe /out:Scalo-Windows.exe /resource:studio.html,studio.html /reference:System.Windows.Forms.dll Scalo.cs
    if($LASTEXITCODE){throw 'Windows compilation failed'}
} finally {Pop-Location}

