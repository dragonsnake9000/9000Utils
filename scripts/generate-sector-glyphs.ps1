Add-Type -AssemblyName System.Drawing
$font=[System.Drawing.Font]::new('Arial',96,[System.Drawing.FontStyle]::Bold,[System.Drawing.GraphicsUnit]::Pixel)
$lines=[System.Collections.Generic.List[string]]::new()
foreach($ch in 'ABCDEFGHIJ0123456789'.ToCharArray()) {
 $bmp=[System.Drawing.Bitmap]::new(150,150)
 $g=[System.Drawing.Graphics]::FromImage($bmp)
 $g.Clear([System.Drawing.Color]::Transparent)
 $g.TextRenderingHint=[System.Drawing.Text.TextRenderingHint]::SingleBitPerPixelGridFit
 $g.DrawString([string]$ch,$font,[System.Drawing.Brushes]::White,0,0,[System.Drawing.StringFormat]::GenericTypographic)
 $minX=150;$maxX=0;$minY=150;$maxY=0
 for($y=0;$y -lt 150;$y++){for($x=0;$x -lt 150;$x++){if($bmp.GetPixel($x,$y).A -gt 127){$minX=[Math]::Min($minX,$x);$maxX=[Math]::Max($maxX,$x);$minY=[Math]::Min($minY,$y);$maxY=[Math]::Max($maxY,$y)}}}
 $lines.Add("$ch $($maxX-$minX+1) $($maxY-$minY+1)")
 for($y=$minY;$y -le $maxY;$y++) { $x=$minX;while($x -le $maxX){if($bmp.GetPixel($x,$y).A -gt 127){$start=$x;while($x -le $maxX -and $bmp.GetPixel($x,$y).A -gt 127){$x++};$lines.Add("$($y-$minY) $($start-$minX) $($x-$minX)")}else{$x++}}}
 $lines.Add('end');$g.Dispose();$bmp.Dispose()
}
$font.Dispose()
[System.IO.File]::WriteAllLines((Join-Path (Split-Path $PSScriptRoot -Parent) 'src/main/resources/assets/utils9000/sector-glyphs.txt'),$lines)
