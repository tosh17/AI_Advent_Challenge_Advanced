#!/bin/bash
set -euo pipefail
cd /home/troot/lora-day9
mkdir -p output/round1
for name in bare.json tuned.json summary.json run_meta.json loss.jsonl; do
  if [ -f "output/$name" ]; then
    cp -n "output/$name" "output/round1/$name"
  fi
done
export OLLAMA_URL=http://10.10.1.101:11434/api/chat
export TEACHER=qwen2.5:7b
export EPOCHS=3
python3 -u ask_teacher.py train
python3 -u ask_teacher.py valid
python3 filter_teacher.py
docker exec ollama ollama stop qwen2.5:7b || true
docker rm -f lora-day9 || true
docker run --name lora-day9 --memory=6g --cpus=3 \
  -v /home/troot/lora-day9:/lab/work \
  -v /home/troot/lora-lab-src/hf-cache:/lab/hf-cache \
  -e HF_HOME=/lab/hf-cache \
  -e HF_HUB_OFFLINE=1 \
  -e TRANSFORMERS_OFFLINE=1 \
  -e OMP_NUM_THREADS=3 \
  -e EPOCHS=3 \
  -w /lab/work \
  lora-lab:latest \
  python run_student.py
python3 make_report.py
echo DONE
