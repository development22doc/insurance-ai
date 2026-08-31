import re
with open(r'D:\Mayur\claimsassist\insurance-ai-platform\agent-service\classpath.txt', 'r') as f:
    content = f.read()
jars = content.strip().split(';')
with open(r'D:\Mayur\claimsassist\insurance-ai-platform\agent-service\deps.xml', 'w') as f:
    for jar in jars:
        jar = jar.strip()
        if jar:
            # Convert Windows path to forward slashes
            jar = jar.replace('\\', '/')
            f.write(f'    <orderEntry type="module-library">\n')
            f.write(f'      <library>\n')
            f.write(f'        <CLASSES>\n')
            f.write(f'          <root url="jar://{jar}!/"/>\n')
            f.write(f'        </CLASSES>\n')
            f.write(f'      </library>\n')
            f.write(f'    </orderEntry>\n')
print(f'Generated {len(jars)} entries')