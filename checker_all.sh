#!/bin/bash

# Verifica se foram passados exatamente 2 argumentos
if [ $# -ne 2 ]; then
  echo "Uso: $0 <diretório_entrada> <diretório_saida>"
  exit 1
fi

# Diretórios de entrada e saída recebidos via terminal
input_directory="$1"
output_directory="$2"

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
