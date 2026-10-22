const fs = require('fs');
const path = require('path');

const dir = path.join(__dirname, '../public/frames');
if (!fs.existsSync(dir)) {
  fs.mkdirSync(dir, { recursive: true });
}

for(let i = 1; i <= 60; i++) {
  const progress = i / 60;
  const scale = 1 + progress * 2;
  const viewBoxSize = 1000 / scale;
  
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${viewBoxSize} ${viewBoxSize}" width="750" height="1334" preserveAspectRatio="xMidYMid slice">
    <rect width="100%" height="100%" fill="#e8f5e9" />
    <path d="M0,0 L${viewBoxSize},0 L${viewBoxSize},${viewBoxSize*0.5} C${viewBoxSize*0.5},${viewBoxSize*0.8} 0,${viewBoxSize*0.4} 0,0" fill="#c8e6c9" />
    <path d="M0,${viewBoxSize} L${viewBoxSize},${viewBoxSize} L${viewBoxSize},${viewBoxSize*0.5} C${viewBoxSize*0.5},${viewBoxSize*0.8} 0,${viewBoxSize*0.4} 0,${viewBoxSize}" fill="#a5d6a7" />
    <pattern id="grid" width="${50/scale}" height="${50/scale}" patternUnits="userSpaceOnUse">
      <path d="M ${50/scale} 0 L 0 0 0 ${50/scale}" fill="none" stroke="rgba(255,255,255,0.4)" stroke-width="${1/scale}"/>
    </pattern>
    <rect width="100%" height="100%" fill="url(#grid)" />
    <text x="50%" y="50%" font-family="sans-serif" font-size="${40/scale}" fill="#2e8b57" text-anchor="middle">Frame ${i}</text>
  </svg>`;
  
  const numStr = i.toString().padStart(3, '0');
  fs.writeFileSync(path.join(dir, `frame_${numStr}.svg`), svg);
}
console.log('Successfully generated 60 SVG frames.');
