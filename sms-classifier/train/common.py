import json
import torch
import torch.nn as nn

MAX_LEN = 256
PAD, UNK = 0, 1

def load_vocab(path="models/vocab.json"):
    with open(path, encoding="utf-8") as f:
        return json.load(f)

def encode(text, vocab, max_len=MAX_LEN):
    """lowercase -> char to index (UNK=1) -> truncate -> pad with 0"""
    ids = [vocab.get(c, UNK) for c in text.lower()[:max_len]]
    ids += [PAD] * (max_len - len(ids))
    return ids

class SmsCharCNN(nn.Module):
    def __init__(self, vocab_size, emb=32, ch=64):
        super().__init__()
        self.emb = nn.Embedding(vocab_size, emb, padding_idx=0)
        self.conv = nn.Conv1d(emb, ch, kernel_size=5, padding=2)
        self.act = nn.ReLU()
        self.fc = nn.Linear(ch, 2)

    def logits(self, x):                      # used for training
        h = self.emb(x).transpose(1, 2)
        h = self.act(self.conv(h))
        h = h.max(dim=2).values
        return self.fc(h)

    def forward(self, x):                     # used for export: [1,160] -> [1,2] probs
        return torch.softmax(self.logits(x), dim=-1)