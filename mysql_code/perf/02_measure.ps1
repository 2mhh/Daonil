# 사용: powershell -File 02_measure.ps1 -Label baseline
# 환경변수 MYSQL_PWD 에 비밀번호를 넣어 두고 실행한다. 대상은 daonil_perf (합성 데이터)이며 daonil 은 건드리지 않는다.
param(
    [string]$Label = "run",
    [int]$Repeat = 5,
    [string]$Mysql = "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe",
    [string]$Db = "daonil_perf"
)
$ErrorActionPreference = "Stop"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$tmp = Join-Path $env:TEMP "perf_explain.sql"
$enc = New-Object System.Text.UTF8Encoding($false)
$rows = @()

foreach ($f in Get-ChildItem (Join-Path $here "queries") -Filter *.sql | Sort-Object Name) {
    $body = [IO.File]::ReadAllText($f.FullName, [Text.Encoding]::UTF8)
    [IO.File]::WriteAllText($tmp, "EXPLAIN ANALYZE " + $body, $enc)
    $times = @(); $plan = ""
    for ($i = 0; $i -lt $Repeat; $i++) {
        $out = cmd /c "`"$Mysql`" -u root --default-character-set=utf8mb4 -r -N $Db < `"$tmp`""
        $text = $out -join "`n"
        $m = [regex]::Match($text, 'actual time=[\d.]+\.\.([\d.]+)')
        if (-not $m.Success) { throw "parse failed for $($f.Name): $text" }
        $times += [double]$m.Groups[1].Value
        if ($i -eq 0) { $plan = $text }
    }
    $sorted = $times | Sort-Object
    $median = $sorted[[int][math]::Floor($sorted.Count / 2)]
    $scan = if ($plan -match 'Table scan') { 'TABLE SCAN' } elseif ($plan -match 'Index (range|lookup|scan)') { 'INDEX' } else { '-' }
    $filesort = if ($plan -match 'Sort:') { 'filesort' } else { '' }
    $rows += [pscustomobject]@{ query = $f.BaseName; median_ms = $median; min_ms = $sorted[0]; max_ms = $sorted[-1]; access = $scan; sort = $filesort }
    Set-Content -Path (Join-Path $here "results\plan_${Label}_$($f.BaseName).txt") -Value $plan -Encoding UTF8
}
$rows | Format-Table -AutoSize
$rows | Export-Csv -Path (Join-Path $here "results\result_$Label.csv") -NoTypeInformation -Encoding UTF8

