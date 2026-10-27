# Generate canonical SVG/Mermaid for six services, then legacy Order/Ticket JPEGs.
# Run from any directory: powershell -File docs/database/render-erd.ps1
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$modelPath = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../order-service/target/erd/reference-model.json'))
& node (Join-Path $PSScriptRoot 'render-erd.mjs') --model-path $modelPath
if ($LASTEXITCODE -ne 0) { throw 'ERD validation/generation failed.' }
$diagramModel = Get-Content -LiteralPath $modelPath -Raw -Encoding UTF8 | ConvertFrom-Json

function Write-ReferenceDiagram([string]$Service, [string]$ImageName, [hashtable]$Layout) {
    $serviceDirectory = Join-Path $PSScriptRoot ($Service + '-service')
    $tables = @{}
    $edges = [System.Collections.Generic.List[object]]::new()
    foreach ($sourceTable in $diagramModel.services.$Service.tables) {
        $name = $sourceTable.name
        $columns = @($sourceTable.columns | ForEach-Object {
            [pscustomobject]@{ Name=$_.name; Type=$_.type; IsPrimary=$_.primary; IsForeign=$_.foreign }
        })
        $tables[$name] = [pscustomobject]@{ Name=$name; Columns=$columns; Height=(60 + $columns.Count * 31); X=0; Y=0 }
        foreach ($foreignKey in $sourceTable.foreignKeys) {
            $edges.Add([pscustomobject]@{ Parent=$foreignKey.parent; Child=$name })
        }
    }
    $rowHeights = @(0, 0, 0)
    foreach ($name in $tables.Keys) {
        if (-not $Layout.ContainsKey($name)) { throw "Missing layout: $name" }
        $row = $Layout[$name][1]
        $rowHeights[$row] = [Math]::Max($rowHeights[$row], $tables[$name].Height)
    }
    $rowY = @(150, (150 + $rowHeights[0] + 110), (150 + $rowHeights[0] + 110 + $rowHeights[1] + 110))
    $lastRow = ($Layout.Values | ForEach-Object { $_[1] } | Measure-Object -Maximum).Maximum
    $canvasHeight = $rowY[$lastRow] + $rowHeights[$lastRow] + 100
    foreach ($name in $tables.Keys) {
        $tables[$name].X = 65 + $Layout[$name][0] * 750
        $tables[$name].Y = $rowY[$Layout[$name][1]]
    }

    $bitmap = [System.Drawing.Bitmap]::new(2280, $canvasHeight)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::ClearTypeGridFit
    $titleFont = [System.Drawing.Font]::new('Segoe UI', 28, [System.Drawing.FontStyle]::Bold)
    $headingFont = [System.Drawing.Font]::new('Segoe UI', 19, [System.Drawing.FontStyle]::Bold)
    $bodyFont = [System.Drawing.Font]::new('Consolas', 15)
    $noteFont = [System.Drawing.Font]::new('Segoe UI', 16)
    $ink = [System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml('#172b4d'))
    $muted = [System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml('#52657d'))
    $uuidBrush = [System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml('#08775d'))
    $headerBrush = [System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml('#163453'))
    $border = [System.Drawing.Pen]::new([System.Drawing.ColorTranslator]::FromHtml('#c3d0dd'), 2)
    $arrow = [System.Drawing.Pen]::new([System.Drawing.ColorTranslator]::FromHtml('#7188a3'), 3)
    $arrow.CustomEndCap = [System.Drawing.Drawing2D.AdjustableArrowCap]::new(5, 6)
    try {
        $graphics.Clear([System.Drawing.Color]::White)
        $graphics.DrawString(($Service.ToUpperInvariant() + ' SERVICE | UUID CHAR(36)'), $titleFont, $ink, 65, 22)
        $graphics.DrawString('Schema tham chiếu từ init.sql — UUID 36 ký tự. DDL SHA-256: ' + $diagramModel.services.$Service.sha.Substring(0,16), $noteFont, $muted, 65, 85)
        $edgeNumber = 0
        foreach ($edge in $edges) {
            $parent = $tables[$edge.Parent]; $child = $tables[$edge.Child]
            $points = [System.Collections.Generic.List[System.Drawing.PointF]]::new()
            if ($parent.Y -eq $child.Y) {
                $points.Add([System.Drawing.PointF]::new(($parent.X + 650), ($parent.Y + 85)))
                $points.Add([System.Drawing.PointF]::new($child.X, ($child.Y + 85)))
            } else {
                $startX = $parent.X + 325 + (($edgeNumber % 3) - 1) * 70
                $targetX = $child.X + 325
                if (($child.Y - $parent.Y) -gt ($rowHeights[0] + 200)) {
                    # Skip an intermediate row through the gutter between table columns.
                    $gutterX = $child.X + 690
                    $points.Add([System.Drawing.PointF]::new($startX, ($parent.Y + $parent.Height)))
                    $points.Add([System.Drawing.PointF]::new($startX, ($parent.Y + $parent.Height + 35)))
                    $points.Add([System.Drawing.PointF]::new($gutterX, ($parent.Y + $parent.Height + 35)))
                    $points.Add([System.Drawing.PointF]::new($gutterX, ($child.Y - 35)))
                    $points.Add([System.Drawing.PointF]::new($targetX, ($child.Y - 35)))
                } else {
                    $midY = $child.Y - 32 - ($edgeNumber % 3) * 17
                    $points.Add([System.Drawing.PointF]::new($startX, ($parent.Y + $parent.Height)))
                    $points.Add([System.Drawing.PointF]::new($startX, $midY))
                    $points.Add([System.Drawing.PointF]::new($targetX, $midY))
                }
                $points.Add([System.Drawing.PointF]::new($targetX, $child.Y))
            }
            $graphics.DrawLines($arrow, $points.ToArray())
            $edgeNumber++
        }
        foreach ($table in $tables.Values) {
            $graphics.FillRectangle([System.Drawing.Brushes]::White, $table.X, $table.Y, 650, $table.Height)
            $graphics.DrawRectangle($border, $table.X, $table.Y, 650, $table.Height)
            $graphics.FillRectangle($headerBrush, $table.X, $table.Y, 650, 48)
            $graphics.DrawString($table.Name, $headingFont, [System.Drawing.Brushes]::White, ($table.X + 18), ($table.Y + 6))
            $line = 0
            foreach ($column in $table.Columns) {
                $brush = $ink
                if ($column.Type -eq 'CHAR(36)') { $brush = $uuidBrush }
                $label = $column.Name
                if ($column.IsPrimary) { $label += ' [PK]' }
                elseif ($column.IsForeign) { $label += ' [FK]' }
                $graphics.DrawString($label, $bodyFont, $brush, ($table.X + 16), ($table.Y + 55 + $line * 31))
                $graphics.DrawString($column.Type, $bodyFont, $brush, ($table.X + 440), ($table.Y + 55 + $line * 31))
                $line++
            }
        }
        $graphics.DrawString('Mũi tên: bảng cha → bảng con (FK nội bộ). ID xuyên service là tham chiếu mềm, không tạo FK.', $noteFont, $muted, 65, ($canvasHeight - 65))
        $bitmap.Save((Join-Path $serviceDirectory $ImageName), [System.Drawing.Imaging.ImageFormat]::Jpeg)
    } finally {
        foreach ($resource in @($graphics, $bitmap, $titleFont, $headingFont, $bodyFont, $noteFont, $ink, $muted, $uuidBrush, $headerBrush, $border, $arrow)) { $resource.Dispose() }
    }
}

Write-ReferenceDiagram 'order' '1.JPEG' @{
    orders=@(1,0); order_items=@(0,1); payments=@(1,1); refund_requests=@(2,1)
    order_holders=@(0,2); payment_webhooks=@(1,2); outbox_events=@(2,2)
}
Write-ReferenceDiagram 'ticket' '1.jpeg' @{
    ticket_types=@(0,0); reservations=@(1,0); outbox_events=@(2,0)
    reservation_items=@(0,1); tickets=@(1,1); ticket_scans=@(2,1)
}
