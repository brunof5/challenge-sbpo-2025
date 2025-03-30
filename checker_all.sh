#!/bin/bash

# Caminho para os diretórios (mude conforme necessidade)
# Observação: O nome das instâncias de entrada/saída devem ser os mesmos
input_directory="./datasets/a"
output_directory="./output_greedy"

# Verifica se os diretórios de entrada e saída existem
if [ ! -d "$input_directory" ]; then
  echo "O diretório de entrada não existe!"
  exit 1
fi

if [ ! -d "$output_directory" ]; then
  echo "O diretório de saída não existe!"
  exit 1
fi

# Loop sobre todos os arquivos .txt no diretório de entrada
for input_file in "$input_directory"/*.txt; do
  # Nome base do arquivo (sem a extensão)
  base_name=$(basename "$input_file" .txt)
  
  # Caminho correspondente no diretório de saída
  output_file="$output_directory/$base_name.txt"
  
  # Verifica se o arquivo de saída existe
  if [ ! -f "$output_file" ]; then
    echo "Arquivo de saída correspondente não encontrado para $input_file"
    continue
  fi

  # Chama o script Python passando os arquivos de entrada e saída
  echo "$input_file"
  python3 checker.py "$input_file" "$output_file"
  echo
done
