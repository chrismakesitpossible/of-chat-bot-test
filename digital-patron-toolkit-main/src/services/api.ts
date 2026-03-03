const API_BASE_URL = 'http://localhost:8081/api';

export interface CreatorOnboardingRequest {
  name: string;
  onlyfansUrl: string;
  onlyfansApiKey: string;
  onlyfansAccountId: string;
  tone?: string;
  trackingCode?: string;
}

export interface CreatorOnboardingResponse {
  success: boolean;
  message: string;
  creatorId?: string;
  webhookUrl?: string;
}

export interface CreatorSummary {
  id: number;
  creatorId: string;
  name: string;
  onlyfansUrl: string;
  accountId: string;
  createdAt: string;
}

class ApiService {
  private async request<T>(
    endpoint: string,
    options: RequestInit = {}
  ): Promise<T> {
    const url = `${API_BASE_URL}${endpoint}`;
    
    const config: RequestInit = {
      headers: {
        'Content-Type': 'application/json',
        ...options.headers,
      },
      ...options,
    };

    try {
      const response = await fetch(url, config);
      
      if (!response.ok) {
        const errorData = await response.json().catch(() => ({}));
        throw new Error(errorData.message || `HTTP ${response.status}: ${response.statusText}`);
      }

      return await response.json();
    } catch (error) {
      console.error('API request failed:', error);
      throw error;
    }
  }

  async onboardCreator(data: CreatorOnboardingRequest): Promise<CreatorOnboardingResponse> {
    return this.request<CreatorOnboardingResponse>('/creators/onboard', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  async getCreators(): Promise<CreatorSummary[]> {
    return this.request<CreatorSummary[]>('/creators');
  }

  async getCreator(creatorId: string): Promise<any> {
    return this.request<any>(`/creators/${creatorId}`);
  }

  async updateCreator(creatorId: string, data: Partial<CreatorOnboardingRequest>): Promise<CreatorOnboardingResponse> {
    return this.request<CreatorOnboardingResponse>(`/creators/${creatorId}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  }

  async deleteCreator(creatorId: string): Promise<CreatorOnboardingResponse> {
    return this.request<CreatorOnboardingResponse>(`/creators/${creatorId}`, {
      method: 'DELETE',
    });
  }
}

export const apiService = new ApiService();
