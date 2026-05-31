export type Asset = {
  id: string;
  kind: "screenshot" | "video" | "cover" | string;
  mime_type: string | null;
  file_name: string;
  size_bytes: number;
  sha256: string;
  url: string;
  created_at: string;
};

export type Note = {
  id: string;
  type: "screenshot" | "douyin" | string;
  title: string | null;
  source_url: string | null;
  source_text: string | null;
  author: string | null;
  remark: string | null;
  ocr_text: string | null;
  status: "ready" | "processing" | string;
  created_at: string;
  updated_at: string;
  tags: string[];
  assets: Asset[];
};

export type LoginResponse = {
  access_token: string;
  token_type: string;
  expires_at: string;
};

