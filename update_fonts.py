import os
import re

def adjust_text_size(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    def replace_size(match):
        size = int(match.group(1))
        new_size = size + 2
        return f'android:textSize="{new_size}sp"'

    new_content = re.sub(r'android:textSize="(\d+)sp"', replace_size, content)
    
    if new_content != content:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(new_content)
        print(f'Adjusted sizes in {filepath}')

for root, _, files in os.walk('app/src/main/res/layout'):
    for file in files:
        if file.endswith('.xml'):
            adjust_text_size(os.path.join(root, file))
            
for root, _, files in os.walk('app/src/main/res/layout-land'):
    for file in files:
        if file.endswith('.xml'):
            adjust_text_size(os.path.join(root, file))
