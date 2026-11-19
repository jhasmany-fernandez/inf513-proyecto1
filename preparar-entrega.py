from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
raiz=Path(__file__).resolve().parent
salida=raiz/'entrega'/'2026-2_INF513-P1_grupo22sc.zip'
salida.parent.mkdir(exist_ok=True)
nombres=['pom.xml','README.md','README-WINDOWS.md','.env.remoto.example','.gitignore','ejecutar-local.sh','ejecutar-remoto.sh','ejecutar-windows.ps1','mvn-local.sh','preparar-entrega.py']
docs=['aplicar-relaciones-correo.sql','correo_academia_modelo_actualizado.sql','relaciones-correo.md','pago-facil-checkout.md','implementacion-requisitos.md','despliegue-servidor.md']
archivos=[raiz/n for n in nombres]+[raiz/'docs'/n for n in docs]
archivos += [p for p in (raiz/'src').rglob('*') if p.is_file() and p.suffix in {'.java','.sql'}]
archivos += [raiz/'despliegue'/'receptor-servidor.sh',raiz/'despliegue'/'correo-academia.service.example']
with ZipFile(salida,'w',ZIP_DEFLATED) as z:
    for p in archivos:z.write(p,Path('correo_academia')/p.relative_to(raiz))
print(salida)
